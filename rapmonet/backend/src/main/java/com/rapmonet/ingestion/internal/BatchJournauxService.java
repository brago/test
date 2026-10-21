package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.BatchJournaux;
import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BatchJournauxService implements BatchJournaux {

    private static final Logger LOG = LoggerFactory.getLogger(BatchJournauxService.class);

    static final String TRAITEMENT = "EJOURNAL";
    static final String UTILISATEUR_BATCH = "BATCH";
    /** Fenêtre de rattrapage : si le serveur était arrêté à l'heure prévue, le traitement part à son redémarrage. */
    static final Duration RATTRAPAGE = Duration.ofHours(6);
    /** Au-delà, une exécution restée « EN_COURS » est considérée comme interrompue (arrêt brutal du serveur). */
    private static final Duration EXECUTION_ABANDONNEE = Duration.ofHours(6);
    private static final Pattern HEURE = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final IntegrationJournaux integration;
    private final JournalAudit audit;
    private final TaskExecutor executeur;
    private final Duration delaiStabilite;
    private final ZoneId fuseau;

    BatchJournauxService(JdbcClient jdbc, JsonMapper json, IntegrationJournaux integration, JournalAudit audit,
                         @Qualifier("applicationTaskExecutor") TaskExecutor executeur,
                         @Value("${rapmonet.batch.delai-stabilite-secondes:120}") long delaiStabilite,
                         @Value("${rapmonet.batch.fuseau-horaire:}") String fuseau) {
        this.jdbc = jdbc;
        this.json = json;
        this.integration = integration;
        this.audit = audit;
        this.executeur = executeur;
        this.delaiStabilite = Duration.ofSeconds(delaiStabilite);
        this.fuseau = fuseau.isBlank() ? ZoneId.systemDefault() : ZoneId.of(fuseau);
    }

    // ------------------------------------------------------------------ configuration

    private String parametre(String cle, String defaut) {
        return jdbc.sql("select valeur from parametre_application where cle = ?").param(cle)
                .query(String.class).optional().orElse(defaut);
    }

    Configuration configuration() {
        return new Configuration(Boolean.parseBoolean(parametre("ejournal.batch.actif", "true")),
                parametre("ejournal.batch.heure", "04:00"),
                parametre("ejournal.batch.repertoire_entree", "donnees/ejournal/entree"),
                parametre("ejournal.batch.repertoire_archive", "donnees/ejournal/archive"),
                parametre("ejournal.batch.repertoire_rejet", "donnees/ejournal/rejet"));
    }

    @Override
    public Configuration modifier(Configuration c, String utilisateur) {
        if (c.heure() == null || !HEURE.matcher(c.heure()).matches()) {
            throw new RegleMetierException("Heure invalide : format HH:mm attendu (ex. 04:00).");
        }
        List<String> dossiers = List.of(texte(c.repertoireEntree(), "d'entrée"), texte(c.repertoireArchive(), "d'archive"),
                texte(c.repertoireRejet(), "de rejet"));
        if (dossiers.stream().map(d -> Path.of(d).toAbsolutePath().normalize()).distinct().count() < 3) {
            throw new RegleMetierException("Les dossiers d'entrée, d'archive et de rejet doivent être différents.");
        }
        for (String d : dossiers) {
            try {
                Files.createDirectories(Path.of(d));
            } catch (IOException | RuntimeException e) {
                throw new RegleMetierException("Dossier inaccessible : " + d + " (" + e.getMessage() + ")");
            }
        }
        enregistrer("ejournal.batch.actif", String.valueOf(c.actif()));
        enregistrer("ejournal.batch.heure", c.heure());
        enregistrer("ejournal.batch.repertoire_entree", dossiers.get(0));
        enregistrer("ejournal.batch.repertoire_archive", dossiers.get(1));
        enregistrer("ejournal.batch.repertoire_rejet", dossiers.get(2));
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_BATCH, "Batch journaux GAB",
                "%s, %s, entrée %s".formatted(c.actif() ? "actif" : "désactivé", c.heure(), dossiers.get(0)), true);
        return configuration();
    }

    private static String texte(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new RegleMetierException("Le dossier " + nom + " est obligatoire.");
        }
        return valeur.strip();
    }

    private void enregistrer(String cle, String valeur) {
        jdbc.sql("update parametre_application set valeur = ?, modifie_le = now() where cle = ?").params(valeur, cle).update();
    }

    // ------------------------------------------------------------------ planification

    /** Vrai si l'exécution planifiée du jour est due (heure atteinte, pas encore faite, dans la fenêtre de rattrapage). */
    boolean executionPlanifieeDue(Instant maintenant) {
        Configuration c = configuration();
        if (!c.actif()) {
            return false;
        }
        LocalDateTime prevue = LocalDate.ofInstant(maintenant, fuseau).atTime(LocalTime.parse(c.heure()));
        Instant debut = prevue.atZone(fuseau).toInstant();
        if (maintenant.isBefore(debut) || maintenant.isAfter(debut.plus(RATTRAPAGE))) {
            return false;
        }
        return jdbc.sql("select count(*) from batch_execution where traitement = ? and declencheur = 'PLANIFIE' and debut >= ?")
                .params(TRAITEMENT, OffsetDateTime.ofInstant(debut, fuseau)).query(Integer.class).single() == 0;
    }

    OffsetDateTime prochaineExecution(Instant maintenant) {
        Configuration c = configuration();
        if (!c.actif()) {
            return null;
        }
        LocalDate jour = LocalDate.ofInstant(maintenant, fuseau);
        for (int i = 0; i < 2; i++) {
            Instant prevue = jour.plusDays(i).atTime(LocalTime.parse(c.heure())).atZone(fuseau).toInstant();
            if (prevue.isAfter(maintenant)) {
                return OffsetDateTime.ofInstant(prevue, fuseau);
            }
            if (i == 0 && executionPlanifieeDue(maintenant)) {
                return OffsetDateTime.ofInstant(maintenant, fuseau);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ exécution

    @Override
    public long lancer(String utilisateur) {
        long id = demarrer("MANUEL", utilisateur);
        executeur.execute(() -> traiter(id, utilisateur));
        return id;
    }

    @Override
    public Execution executer(String declencheur, String utilisateur) {
        long id = demarrer(declencheur, utilisateur);
        traiter(id, utilisateur);
        return execution(id);
    }

    /** Crée l'exécution « EN_COURS » ; l'index unique garantit qu'une seule tourne à la fois. */
    private long demarrer(String declencheur, String utilisateur) {
        jdbc.sql("""
                        update batch_execution set statut = 'INTERROMPU', fin = now(),
                            message = 'Exécution abandonnée (arrêt du serveur ?)'
                        where traitement = ? and statut = 'EN_COURS' and debut < ?""")
                .params(TRAITEMENT, OffsetDateTime.now().minus(EXECUTION_ABANDONNEE)).update();
        try {
            var cle = new GeneratedKeyHolder();
            jdbc.sql("insert into batch_execution (traitement, declencheur, lance_par, statut) values (?, ?, ?, 'EN_COURS')")
                    .params(TRAITEMENT, declencheur, utilisateur).update(cle, "id");
            return cle.getKeyAs(Long.class);
        } catch (DuplicateKeyException e) {
            throw new RegleMetierException("Une intégration automatique des journaux est déjà en cours.");
        }
    }

    private void traiter(long id, String utilisateur) {
        Configuration c = configuration();
        List<FichierTraite> detail = new ArrayList<>();
        String statut;
        String message;
        try {
            Path entree = Path.of(c.repertoireEntree());
            Files.createDirectories(entree);
            for (Path fichier : fichiersPrets(entree)) {
                detail.add(traiterFichier(entree, fichier, c));
                mettreAJour(id, "EN_COURS", null, detail);
            }
            long rejetes = detail.stream().filter(f -> f.resultat().equals("REJETE")).count();
            statut = rejetes > 0 ? "TERMINE_AVEC_REJETS" : "TERMINE";
            message = detail.isEmpty() ? "Aucun journal à intégrer dans " + entree.toAbsolutePath() : null;
        } catch (Exception e) {
            LOG.error("Batch journaux GAB en erreur", e);
            statut = "ERREUR";
            message = e.getClass().getSimpleName() + " : " + e.getMessage();
        }
        mettreAJour(id, statut, message, detail);
        Execution ex = execution(id);
        audit.enregistrerPour(utilisateur, ActionsAudit.BATCH_EJOURNAL, "Batch journaux GAB (" + ex.declencheur() + ")",
                "%s : %d fichier(s), %d intégré(s), %d déjà intégré(s), %d rejeté(s), %d opération(s)%s".formatted(
                        statut, ex.nbFichiers(), ex.nbIntegres(), ex.nbDejaIntegres(), ex.nbRejetes(), ex.nbOperations(),
                        message == null ? "" : " – " + message),
                !statut.equals("ERREUR"));
    }

    /** Fichiers stables (non modifiés depuis le délai de stabilité : copie terminée), triés par chemin. */
    private List<Path> fichiersPrets(Path entree) throws IOException {
        Instant limite = Instant.now().minus(delaiStabilite);
        try (Stream<Path> flux = Files.walk(entree)) {
            return flux.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant().isBefore(limite);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted().toList();
        }
    }

    private FichierTraite traiterFichier(Path entree, Path fichier, Configuration c) {
        String relatif = entree.relativize(fichier).toString();
        try {
            IntegrationJournaux.Resultat r = integration.integrer(fichier.getFileName().toString(),
                    Files.readAllBytes(fichier), UTILISATEUR_BATCH);
            boolean dejaIntegre = !r.integre() && r.anomalies().stream()
                    .anyMatch(a -> a.gravite().equals("BLOQUANTE") && a.message().contains("déjà intégré"));
            String resultat = r.integre() ? "INTEGRE" : dejaIntegre ? "DEJA_INTEGRE" : "REJETE";
            String message = r.integre() ? null : r.anomalies().stream().filter(a -> a.gravite().equals("BLOQUANTE"))
                    .map(a -> a.message()).collect(Collectors.joining(" ; "));
            Path destination;
            if (resultat.equals("REJETE")) {
                destination = deplacer(fichier, Path.of(c.repertoireRejet()).resolve(LocalDate.now(fuseau).toString()));
                Files.writeString(destination.resolveSibling(destination.getFileName() + ".erreur.txt"),
                        "Journal refusé le " + LocalDateTime.now(fuseau) + "\n" + r.anomalies().stream()
                                .map(a -> a.gravite() + " : " + a.message()).collect(Collectors.joining("\n")) + "\n",
                        StandardCharsets.UTF_8);
            } else {
                String terminal = r.terminal() != null ? r.terminal() : "inconnu";
                String mois = r.dateFin() != null ? r.dateFin().toString().substring(0, 7) : LocalDate.now(fuseau).toString().substring(0, 7);
                destination = deplacer(fichier, Path.of(c.repertoireArchive()).resolve(terminal).resolve(mois));
            }
            return new FichierTraite(relatif, resultat, r.terminal(), r.nbOperations(), r.nbDoublons(), message,
                    destination.toString());
        } catch (Exception e) {
            LOG.warn("Journal {} non traité", fichier, e);
            return new FichierTraite(relatif, "REJETE", null, 0, 0,
                    "Erreur technique : " + e.getMessage() + " (fichier laissé dans le dossier d'entrée)", fichier.toString());
        }
    }

    /** Déplace le fichier dans le dossier cible, sans écraser un fichier du même nom. */
    private static Path deplacer(Path fichier, Path dossier) throws IOException {
        Files.createDirectories(dossier);
        String nom = fichier.getFileName().toString();
        Path cible = dossier.resolve(nom);
        for (int i = 1; Files.exists(cible); i++) {
            cible = dossier.resolve(nom + "." + i);
        }
        return Files.move(fichier, cible, StandardCopyOption.ATOMIC_MOVE);
    }

    private void mettreAJour(long id, String statut, String message, List<FichierTraite> detail) {
        jdbc.sql("""
                        update batch_execution set statut = ?, message = ?, detail = ?::jsonb, nb_fichiers = ?,
                            nb_integres = ?, nb_deja_integres = ?, nb_rejetes = ?, nb_operations = ?,
                            fin = case when ? = 'EN_COURS' then null else now() end
                        where id = ?""")
                .params(statut, message, json.writeValueAsString(detail), detail.size(),
                        compter(detail, "INTEGRE"), compter(detail, "DEJA_INTEGRE"), compter(detail, "REJETE"),
                        detail.stream().mapToInt(FichierTraite::nbOperations).sum(), statut, id)
                .update();
    }

    private static int compter(List<FichierTraite> detail, String resultat) {
        return (int) detail.stream().filter(f -> f.resultat().equals(resultat)).count();
    }

    // ------------------------------------------------------------------ consultation

    @Override
    public Etat etat() {
        Configuration c = configuration();
        int enAttente = 0;
        try {
            Path entree = Path.of(c.repertoireEntree());
            if (Files.isDirectory(entree)) {
                try (Stream<Path> flux = Files.walk(entree)) {
                    enAttente = (int) flux.filter(Files::isRegularFile).count();
                }
            }
        } catch (IOException | RuntimeException e) {
            enAttente = -1;
        }
        boolean enCours = jdbc.sql("select count(*) from batch_execution where traitement = ? and statut = 'EN_COURS'")
                .param(TRAITEMENT).query(Integer.class).single() > 0;
        List<Execution> dernieres = jdbc.sql("select id from batch_execution where traitement = ? order by debut desc limit 20")
                .param(TRAITEMENT).query(Long.class).list().stream().map(this::execution).toList();
        return new Etat(c, prochaineExecution(Instant.now()), enCours, enAttente, dernieres);
    }

    Execution execution(long id) {
        return jdbc.sql("select * from batch_execution where id = ?").param(id)
                .query((rs, n) -> new Execution(rs.getLong("id"), rs.getString("declencheur"), rs.getString("lance_par"),
                        rs.getObject("debut", OffsetDateTime.class), rs.getObject("fin", OffsetDateTime.class),
                        rs.getString("statut"), rs.getInt("nb_fichiers"), rs.getInt("nb_integres"),
                        rs.getInt("nb_deja_integres"), rs.getInt("nb_rejetes"), rs.getInt("nb_operations"),
                        rs.getString("message"),
                        json.readValue(rs.getString("detail"), new TypeReference<List<FichierTraite>>() { })))
                .single();
    }

}
