package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.CoupuresGab;
import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.ingestion.RapportIntegration.AnomalieLis;
import com.rapmonet.ingestion.ejournal.EvenementGab;
import com.rapmonet.ingestion.ejournal.LecteurJournalNcr;
import com.rapmonet.ingestion.ejournal.OperationGab;
import com.rapmonet.ingestion.ejournal.ResultatLectureJournal;
import com.rapmonet.ingestion.ejournal.SessionGab;
import com.rapmonet.ingestion.ejournal.TypeOperationGab;
import com.rapmonet.ingestion.moteur.Anomalie;
import com.rapmonet.ingestion.moteur.Gravite;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
class IntegrationJournauxService implements IntegrationJournaux {

    /** Nom de fichier : <terminal>_…_<AAAAMMJJ>… (ex. 99990001_99990001_20260625_1.jrn…). */
    private static final Pattern NOM_TERMINAL = Pattern.compile("^(\\d{4,})_");
    private static final Pattern NOM_DATE = Pattern.compile("_(20\\d{6})(?:_|\\.|$)");

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final JournalAudit audit;

    private final ObjectProvider<CoupuresGab> coupuresGab;

    IntegrationJournauxService(JdbcClient jdbc, JsonMapper json, JournalAudit audit, ObjectProvider<CoupuresGab> coupuresGab) {
        this.coupuresGab = coupuresGab;
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    @Override
    @Transactional
    public Resultat integrer(String nom, byte[] contenu, String utilisateur) {
        String empreinte = sha256(contenu);
        String format = jdbc.sql("select valeur from parametre_application where cle = 'ejournal.ncr.format_date'")
                .query(String.class).optional().orElse(LecteurJournalNcr.FORMAT_DATE_DEFAUT);
        ResultatLectureJournal lecture = new LecteurJournalNcr(format)
                .lire(LecteurJournalNcr.lignes(new String(contenu, StandardCharsets.ISO_8859_1)));
        List<Anomalie> anomalies = new ArrayList<>(lecture.anomalies());

        jdbc.sql("select nom, integre_le from ej_fichier where empreinte_sha256 = ?").param(empreinte)
                .query((rs, n) -> "Fichier déjà intégré (« %s », le %s).".formatted(
                        rs.getString("nom"), rs.getObject("integre_le", OffsetDateTime.class).toLocalDate()))
                .optional().ifPresent(m -> anomalies.addFirst(Anomalie.bloquante(null, m)));

        Matcher mt = NOM_TERMINAL.matcher(nom == null ? "" : nom);
        String terminal = lecture.terminal() != null ? lecture.terminal() : mt.find() ? mt.group(1) : null;
        if (terminal == null) {
            anomalies.add(Anomalie.bloquante(null, "Identifiant du GAB introuvable (ni « NUM GAB » dans les tickets, "
                    + "ni nom de fichier de la forme <TerminalID>_AAAAMMJJ.jrn)."));
        }
        controlerDates(nom, lecture, anomalies, format);
        List<Long> coupures = coupures(terminal, anomalies);

        List<LocalDate> dates = lecture.operations().stream().map(OperationGab::dateHeure).filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate).sorted().toList();
        LocalDate debut = dates.isEmpty() ? null : dates.getFirst();
        LocalDate fin = dates.isEmpty() ? null : dates.getLast();
        boolean integre = anomalies.stream().noneMatch(a -> a.gravite() == Gravite.BLOQUANTE);

        int[] compte = {0, 0, 0}; // opérations, doublons, retraits approuvés
        long[] montant = {0};
        Long id = null;
        if (integre) {
            id = enregistrerFichier(nom, empreinte, terminal, debut, fin, lecture, utilisateur);
            enregistrerContenu(id, terminal, lecture, compte, montant, coupures, anomalies);
            enregistrerAnomalies(id, anomalies);
            int nbAvertissements = (int) anomalies.stream().filter(a -> a.gravite() == Gravite.AVERTISSEMENT).count();
            jdbc.sql("""
                            update ej_fichier set nb_operations = ?, nb_doublons = ?, nb_retraits_approuves = ?,
                                montant_retraits = ?, nb_avertissements = ? where id = ?""")
                    .params(compte[0], compte[1], compte[2], montant[0], nbAvertissements, id).update();
        }
        audit.enregistrerPour(utilisateur, ActionsAudit.EJOURNAL_INTEGRATION, nom, integre
                ? "GAB %s du %s au %s : %d opération(s), %d doublon(s)".formatted(terminal, debut, fin, compte[0], compte[1])
                : "Rejeté : " + anomalies.stream().filter(a -> a.gravite() == Gravite.BLOQUANTE).count() + " anomalie(s) bloquante(s)",
                integre);
        return new Resultat(nom, integre, id, terminal, debut, fin, lecture.sessions().size(), compte[0], compte[1],
                compte[2], montant[0], lecture.evenements().size(),
                anomalies.stream().map(a -> new AnomalieLis(a.ligne(), a.gravite().name(), a.message())).toList());
    }

    /** Un mauvais format de date donne des dates valides mais fausses : comparaison avec la date du nom de fichier. */
    private static void controlerDates(String nom, ResultatLectureJournal lecture, List<Anomalie> anomalies, String format) {
        Matcher m = NOM_DATE.matcher(nom == null ? "" : nom);
        if (!m.find()) {
            return;
        }
        LocalDate dateNom = LocalDate.parse(m.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        long incoherentes = lecture.operations().stream().map(OperationGab::dateHeure).filter(Objects::nonNull)
                .filter(d -> Math.abs(ChronoUnit.DAYS.between(d.toLocalDate(), dateNom)) > 366).count();
        if (incoherentes > 0) {
            anomalies.add(Anomalie.avertissement(null, ("%d ticket(s) daté(s) à plus d'un an de la date du fichier (%s) : "
                    + "vérifiez le format de date paramétré (« %s »).").formatted(incoherentes, dateNom, format)));
        }
    }

    private long enregistrerFichier(String nom, String empreinte, String terminal, LocalDate debut, LocalDate fin,
                                    ResultatLectureJournal lecture, String utilisateur) {
        var cle = new GeneratedKeyHolder();
        jdbc.sql("""
                        insert into ej_fichier (nom, empreinte_sha256, terminal_id, date_debut, date_fin, nb_sessions,
                            nb_operations, nb_doublons, nb_retraits_approuves, montant_retraits, nb_evenements,
                            nb_avertissements, integre_par)
                        values (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?, 0, ?)""")
                .params(nom, empreinte, terminal, debut, fin, lecture.sessions().size(), lecture.evenements().size(), utilisateur)
                .update(cle, "id");
        return cle.getKeyAs(Long.class);
    }

    private void enregistrerContenu(long id, String terminal, ResultatLectureJournal lecture, int[] compte, long[] montant,
                                    List<Long> coupures, List<Anomalie> anomalies) {
        List<SessionGab> sessions = lecture.sessions();
        LocalDate dateConnue = null;
        for (int i = 0; i < sessions.size(); i++) {
            SessionGab s = sessions.get(i);
            LocalDate date = s.operations().stream().map(OperationGab::dateHeure).filter(Objects::nonNull)
                    .map(LocalDateTime::toLocalDate).findFirst().orElse(null);
            if (date == null) {
                // Session sans ticket : date de la session précédente, sinon de la suivante.
                date = dateConnue != null ? dateConnue : sessions.subList(i, sessions.size()).stream()
                        .flatMap(x -> x.operations().stream()).map(OperationGab::dateHeure).filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate).findFirst().orElse(null);
            } else {
                dateConnue = date;
            }
            var cle = new GeneratedKeyHolder();
            jdbc.sql("""
                            insert into ej_session (fichier_id, rang, numero_ligne, terminal_id, date_session, heure_debut,
                                carte, nb_operations, billets_presentes, billets_pris, compteurs, incidents, complete, texte)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?)""")
                    .params(id, s.rang(), s.numeroLigne(), terminal, date, s.heureDebut(), s.carte(), s.operations().size(),
                            s.billetsPresentes() == null ? null : json.writeValueAsString(s.billetsPresentes()),
                            s.billetsPris(), s.compteurs() == null ? null : json.writeValueAsString(s.compteurs()),
                            json.writeValueAsString(s.incidents()), s.complete(), s.texte())
                    .update(cle, "id");
            long sessionId = cle.getKeyAs(Long.class);
            // Billets présentés : rattachés au retrait approuvé de la session (s'il est seul).
            List<OperationGab> retraitsApprouves = s.operations().stream()
                    .filter(o -> o.type() == TypeOperationGab.RETRAIT && "00".equals(o.codeReponse())).toList();
            Long montantBillets = retraitsApprouves.size() == 1 ? valeur(s.billetsPresentes(), coupures) : null;
            if (montantBillets != null && retraitsApprouves.getFirst().montant() != null
                    && !montantBillets.equals(retraitsApprouves.getFirst().montant())) {
                OperationGab r = retraitsApprouves.getFirst();
                anomalies.add(Anomalie.avertissement(r.numeroLigne(), ("Retrait du %s : montant du ticket %s, billets présentés %s "
                        + "(%s) – écart de %s. Vérifiez la valeur des coupures paramétrée (%s).").formatted(
                        r.dateHeure(), montantTexte(r.montant()), montantTexte(montantBillets),
                        detailBillets(s.billetsPresentes(), coupures), montantTexte(montantBillets - r.montant()),
                        coupures.stream().map(String::valueOf).collect(Collectors.joining(", ")))));
            }
            for (OperationGab o : s.operations()) {
                String statut = statut(o, s);
                String term = o.terminal() != null ? o.terminal() : terminal;
                String unicite = String.join("|", term, o.type().name(), Objects.toString(o.carte(), ""),
                        Objects.toString(o.dateHeure(), "L" + o.numeroLigne()), Objects.toString(o.reference(), ""),
                        Objects.toString(o.montant(), ""), Objects.toString(o.codeReponse(), ""));
                int insere = jdbc.sql("""
                                insert into ej_operation (fichier_id, session_id, terminal_id, type, statut, date_heure, carte,
                                    reference, montant, devise, code_reponse, compte, numero_ligne, cle_unicite, montant_billets)
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (cle_unicite) do nothing""")
                        .params(id, sessionId, term, o.type().name(), statut,
                                o.dateHeure() == null ? null : Timestamp.valueOf(o.dateHeure()), o.carte(), o.reference(),
                                o.montant(), o.devise(), o.codeReponse(), o.compte(), o.numeroLigne(), unicite,
                                retraitsApprouves.size() == 1 && retraitsApprouves.getFirst() == o ? montantBillets : null)
                        .update();
                if (insere == 0) {
                    compte[1]++;
                } else {
                    compte[0]++;
                    if (o.type() == TypeOperationGab.RETRAIT && "00".equals(o.codeReponse())) {
                        compte[2]++;
                        montant[0] += o.montant() == null ? 0 : o.montant();
                    }
                }
            }
        }
        for (EvenementGab e : lecture.evenements()) {
            jdbc.sql("""
                            insert into ej_evenement (fichier_id, terminal_id, date_heure, actions, releves, especes_ajoutees,
                                numero_ligne, texte)
                            values (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)""")
                    .params(id, terminal, e.dateHeure() == null ? null : Timestamp.valueOf(e.dateHeure()),
                            json.writeValueAsString(e.actions()), json.writeValueAsString(e.releves()),
                            json.writeValueAsString(e.especesAjoutees()), e.numeroLigne(), e.texte())
                    .update();
        }
    }

    /**
     * Statut d'une opération : un retrait approuvé est DISTRIBUE si les billets ont été pris,
     * NON_PRIS s'ils ont été présentés sans être pris, APPROUVE si le journal ne détaille pas les billets.
     */
    static String statut(OperationGab o, SessionGab s) {
        if (o.codeReponse() == null) {
            return "INCONNU";
        }
        if (!"00".equals(o.codeReponse())) {
            return "REFUSE";
        }
        if (o.type() == TypeOperationGab.SOLDE || s.billetsPresentes() == null) {
            return "APPROUVE";
        }
        return s.billetsPris() ? "DISTRIBUE" : "NON_PRIS";
    }

    /** Valeur des billets de type 1 à 4 : profil du GAB s'il est paramétré, sinon paramètre ejournal.coupures. */
    private List<Long> coupures(String terminal, List<Anomalie> anomalies) {
        CoupuresGab gab = coupuresGab.getIfAvailable();
        if (gab != null && terminal != null) {
            var duGab = gab.coupuresParType(terminal);
            if (duGab.isPresent()) {
                return duGab.get();
            }
        }
        String valeur = jdbc.sql("select valeur from parametre_application where cle = 'ejournal.coupures'")
                .query(String.class).optional().orElse("");
        List<Long> coupures = lireCoupures(valeur);
        if (coupures == null) {
            anomalies.add(Anomalie.avertissement(null, "Paramètre « ejournal.coupures » invalide (« " + valeur
                    + " ») : montant des billets présentés non contrôlé."));
        }
        return coupures;
    }

    static List<Long> lireCoupures(String valeur) {
        try {
            List<Long> l = Arrays.stream(valeur.split(",")).map(String::strip).map(Long::valueOf).toList();
            return l.isEmpty() || l.stream().anyMatch(v -> v < 0) ? null : l;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Billets présentés × coupures ; null si non calculable. */
    static Long valeur(List<Integer> billets, List<Long> coupures) {
        if (billets == null || coupures == null || billets.size() > coupures.size()) {
            return null;
        }
        long total = 0;
        for (int i = 0; i < billets.size(); i++) {
            total += billets.get(i) * coupures.get(i);
        }
        return total;
    }

    private static String detailBillets(List<Integer> billets, List<Long> coupures) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < billets.size(); i++) {
            if (billets.get(i) > 0) {
                l.add(billets.get(i) + " × " + (coupures.get(i) == 0 ? "type " + (i + 1) + " non chargé"
                        : montantTexte(coupures.get(i))));
            }
        }
        return l.isEmpty() ? "aucun billet" : String.join(" + ", l);
    }

    private static String montantTexte(long v) {
        return String.format(java.util.Locale.FRANCE, "%,d", v).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    private void enregistrerAnomalies(long id, List<Anomalie> anomalies) {
        for (Anomalie a : anomalies) {
            jdbc.sql("insert into ej_anomalie (fichier_id, ligne, gravite, message) values (?, ?, ?, ?)")
                    .params(id, a.ligne(), a.gravite().name(), a.message()).update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichierJournal> lister() {
        return jdbc.sql("select * from ej_fichier order by integre_le desc, id desc")
                .query((rs, n) -> new FichierJournal(rs.getLong("id"), rs.getString("nom"), rs.getString("terminal_id"),
                        rs.getObject("date_debut", LocalDate.class), rs.getObject("date_fin", LocalDate.class),
                        rs.getInt("nb_sessions"), rs.getInt("nb_operations"), rs.getInt("nb_doublons"),
                        rs.getInt("nb_retraits_approuves"), rs.getLong("montant_retraits"), rs.getInt("nb_evenements"),
                        rs.getInt("nb_avertissements"), rs.getObject("integre_le", OffsetDateTime.class),
                        rs.getString("integre_par")))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnomalieLis> anomalies(long fichierId) {
        existe(fichierId);
        return jdbc.sql("select ligne, gravite, message from ej_anomalie where fichier_id = ? order by ligne nulls first, id")
                .param(fichierId)
                .query((rs, n) -> new AnomalieLis((Integer) rs.getObject("ligne"), rs.getString("gravite"), rs.getString("message")))
                .list();
    }

    @Override
    @Transactional
    public void annuler(long fichierId) {
        existe(fichierId);
        String nom = jdbc.sql("select nom || ' (GAB ' || terminal_id || ')' from ej_fichier where id = ?")
                .param(fichierId).query(String.class).single();
        jdbc.sql("delete from ej_fichier where id = ?").param(fichierId).update();
        audit.enregistrer(ActionsAudit.EJOURNAL_ANNULATION, nom, null);
    }

    private void existe(long id) {
        if (jdbc.sql("select count(*) from ej_fichier where id = ?").param(id).query(Integer.class).single() == 0) {
            throw new RessourceIntrouvableException("Journal introuvable : " + id);
        }
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
