package com.rapmonet.gab.internal;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.gab.internal.ParametrageGabService.Cassette;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Approvisionnements des GAB : saisie du billetage par cassette, puis validation par une autre personne. */
@Service
@Transactional
public class ApprosService {

    /** Ligne de billetage : nombre de billets chargés dans une cassette. */
    public record Ligne(String cassette, int type, long coupure, int nombre, long montant) {
    }

    public record Saisie(String terminal, LocalDateTime dateAppro, String gabiste, String referenceExterne,
                         String numeroBon, String commentaire, Map<String, Integer> billets) {
    }

    /**
     * Rapprochement de la saisie avec le passage du gabiste lu dans le journal (billets ajoutés par type).
     *
     * @param statut CONCORDANT, ECART ou ABSENT (aucun ajout d'espèces dans les journaux intégrés ce jour-là)
     */
    public record ControleJournal(String statut, LocalDateTime passage, List<Integer> journal, List<Integer> saisie,
                                  Long montantJournal, String message) {
    }

    public record Appro(long id, String reference, String terminal, String libelleGab, String agence, String compteCaisse,
                        LocalDateTime dateAppro, String gabiste, String referenceExterne, String numeroBon,
                        String commentaire, List<Ligne> billetage, long montant, String statut, String saisiPar,
                        OffsetDateTime saisiLe, String modifiePar, String decidePar, OffsetDateTime decideLe,
                        String motif, String arrete, ControleJournal controleJournal) {
    }

    public record Critere(LocalDate du, LocalDate au, String terminal, String statut) {
    }

    private static final String SELECT = """
            select a.*, g.libelle as libelle_gab, g.agence, g.compte_caisse,
                (select r.reference from gab_arrete r where r.appro_id = a.id and r.statut <> 'ANNULE') as arrete
            from gab_appro a join gab g on g.terminal_id = a.terminal_id
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final JournalAudit audit;
    private final ParametrageGabService parametrage;
    private final JournalGab journal;

    ApprosService(JdbcClient jdbc, JsonMapper json, JournalAudit audit, ParametrageGabService parametrage, JournalGab journal) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.parametrage = parametrage;
        this.journal = journal;
    }

    // ------------------------------------------------------------------ saisie

    public Appro creer(Saisie s, String utilisateur) {
        Valide v = valider(s);
        long id = jdbc.sql("""
                        insert into gab_appro (reference, terminal_id, date_appro, gabiste, reference_externe, numero_bon,
                            commentaire, billetage, montant, statut, saisi_par, modifie_par)
                        values (lpad(nextval('gab_appro_reference')::text, 7, '0'), ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'SAISI', ?, ?)
                        returning id""")
                .params(v.terminal, Timestamp.valueOf(s.dateAppro()), v.gabiste, ParametrageGabService.vide(s.referenceExterne()),
                        ParametrageGabService.vide(s.numeroBon()), ParametrageGabService.vide(s.commentaire()),
                        json.writeValueAsString(v.lignes), v.montant, utilisateur, utilisateur)
                .query(Long.class).single();
        Appro a = appro(id);
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_APPRO_SAISIE, "Appro " + a.reference(), resume(a), true);
        return a;
    }

    public Appro modifier(long id, Saisie s, String utilisateur) {
        Appro avant = appro(id);
        if (!avant.statut().equals("SAISI")) {
            throw new RegleMetierException("Un approvisionnement " + libelle(avant.statut()) + " ne peut plus être modifié.");
        }
        Valide v = valider(s);
        jdbc.sql("""
                        update gab_appro set terminal_id = ?, date_appro = ?, gabiste = ?, reference_externe = ?, numero_bon = ?,
                            commentaire = ?, billetage = ?::jsonb, montant = ?, modifie_par = ?, modifie_le = now()
                        where id = ? and statut = 'SAISI'""")
                .params(v.terminal, Timestamp.valueOf(s.dateAppro()), v.gabiste, ParametrageGabService.vide(s.referenceExterne()),
                        ParametrageGabService.vide(s.numeroBon()), ParametrageGabService.vide(s.commentaire()),
                        json.writeValueAsString(v.lignes), v.montant, utilisateur, id)
                .update();
        Appro a = appro(id);
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_APPRO_SAISIE, "Appro " + a.reference(), "Modification : " + resume(a), true);
        return a;
    }

    private record Valide(String terminal, String gabiste, List<Ligne> lignes, long montant) {
    }

    private Valide valider(Saisie s) {
        String terminal = ParametrageGabService.obligatoire(s.terminal(), "Le GAB");
        ParametrageGabService.Gab gab = parametrage.gab(terminal);
        if (!gab.actif()) {
            throw new RegleMetierException("Le GAB " + terminal + " est désactivé.");
        }
        if (s.dateAppro() == null) {
            throw new RegleMetierException("La date de l'approvisionnement est obligatoire.");
        }
        if (s.dateAppro().isAfter(LocalDateTime.now().plusHours(1))) {
            throw new RegleMetierException("La date de l'approvisionnement ne peut pas être dans le futur.");
        }
        String gabiste = ParametrageGabService.obligatoire(s.gabiste(), "Le gabiste");
        List<Ligne> lignes = billetage(parametrage.profil(gab.profilCode()).cassettes(), s.billets());
        long montant = lignes.stream().mapToLong(Ligne::montant).sum();
        if (montant == 0) {
            throw new RegleMetierException("Le billetage est vide : saisissez le nombre de billets d'au moins une cassette.");
        }
        return new Valide(terminal, gabiste, lignes, montant);
    }

    /** Billetage d'après les cassettes du profil du GAB (coupure et type imposés par le paramétrage). */
    static List<Ligne> billetage(List<Cassette> cassettes, Map<String, Integer> billets) {
        Map<String, Integer> saisis = billets == null ? Map.of() : billets;
        for (String c : saisis.keySet()) {
            if (cassettes.stream().noneMatch(x -> x.cassette().equals(c))) {
                throw new RegleMetierException("La cassette " + c + " n'existe pas dans le profil du GAB.");
            }
        }
        List<Ligne> lignes = new ArrayList<>();
        for (Cassette c : cassettes) {
            int n = saisis.getOrDefault(c.cassette(), 0) == null ? 0 : saisis.getOrDefault(c.cassette(), 0);
            if (n < 0) {
                throw new RegleMetierException("Cassette " + c.cassette() + " : nombre de billets négatif.");
            }
            lignes.add(new Ligne(c.cassette(), c.type(), c.coupure(), n, n * c.coupure()));
        }
        return lignes;
    }

    // ------------------------------------------------------------------ validation à quatre yeux

    public Appro valider(long id, String utilisateur) {
        Appro a = appro(id);
        if (!a.statut().equals("SAISI")) {
            throw new RegleMetierException("Cet approvisionnement est déjà " + libelle(a.statut()) + ".");
        }
        if (utilisateur.equalsIgnoreCase(a.saisiPar()) || utilisateur.equalsIgnoreCase(a.modifiePar())) {
            throw new RegleMetierException("Validation à quatre yeux : l'approvisionnement doit être validé par une autre "
                    + "personne que celle qui l'a saisi ou modifié.");
        }
        jdbc.sql("update gab_appro set statut = 'VALIDE', decide_par = ?, decide_le = now() where id = ? and statut = 'SAISI'")
                .params(utilisateur, id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_APPRO_VALIDATION, "Appro " + a.reference(), resume(a), true);
        return appro(id);
    }

    public Appro annuler(long id, String motif, String utilisateur) {
        Appro a = appro(id);
        String m = ParametrageGabService.obligatoire(motif, "Le motif de l'annulation");
        if (a.statut().equals("ANNULE")) {
            throw new RegleMetierException("Cet approvisionnement est déjà annulé.");
        }
        if (a.arrete() != null) {
            throw new RegleMetierException("Cet approvisionnement est clôturé par l'arrêté " + a.arrete()
                    + " : annulez d'abord l'arrêté.");
        }
        jdbc.sql("update gab_appro set statut = 'ANNULE', decide_par = ?, decide_le = now(), motif = ? where id = ?")
                .params(utilisateur, m, id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_APPRO_ANNULATION, "Appro " + a.reference(), m, true);
        return appro(id);
    }

    // ------------------------------------------------------------------ consultation

    @Transactional(readOnly = true)
    public Appro appro(long id) {
        Appro a = jdbc.sql(SELECT + " where a.id = ?").param(id).query((rs, n) -> appro(rs)).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Approvisionnement introuvable."));
        return avecControle(a);
    }

    @Transactional(readOnly = true)
    public PageResultat<Appro> rechercher(Critere c, int page, int taille) {
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        long total = jdbc.sql("select count(*) from gab_appro a" + where).params(params).query(Long.class).single();
        List<Object> p = new ArrayList<>(params);
        p.add(taille);
        p.add((long) page * taille);
        return new PageResultat<>(jdbc.sql(SELECT + where + " order by a.date_appro desc, a.id desc limit ? offset ?").params(p)
                .query((rs, n) -> appro(rs)).list().stream().map(this::avecControle).toList(), total, page, taille);
    }

    @Transactional(readOnly = true)
    public void parcourir(Critere c, Consumer<Appro> consommateur) {
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        jdbc.sql(SELECT + where + " order by a.date_appro desc, a.id desc").params(params).query((rs, n) -> appro(rs)).list()
                .forEach(consommateur);
    }

    /** Approvisionnements validés pas encore arrêtés, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Appro> nonArretes(String terminal) {
        return jdbc.sql(SELECT + " where a.terminal_id = ? and a.statut = 'VALIDE' and not exists "
                        + "(select 1 from gab_arrete r where r.appro_id = a.id and r.statut <> 'ANNULE') order by a.date_appro desc")
                .param(terminal).query((rs, n) -> appro(rs)).list();
    }

    private static String where(Critere c, List<Object> params) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (c.du() != null) {
            w.append(" and a.date_appro >= ?");
            params.add(Timestamp.valueOf(c.du().atStartOfDay()));
        }
        if (c.au() != null) {
            w.append(" and a.date_appro < ?");
            params.add(Timestamp.valueOf(c.au().plusDays(1).atStartOfDay()));
        }
        if (c.terminal() != null && !c.terminal().isBlank()) {
            w.append(" and a.terminal_id = ?");
            params.add(c.terminal());
        }
        if (c.statut() != null && !c.statut().isBlank()) {
            w.append(" and a.statut = ?");
            params.add(c.statut());
        }
        return w.toString();
    }

    private Appro appro(ResultSet rs) throws SQLException {
        return new Appro(rs.getLong("id"), rs.getString("reference"), rs.getString("terminal_id"), rs.getString("libelle_gab"),
                rs.getString("agence"), rs.getString("compte_caisse"), rs.getTimestamp("date_appro").toLocalDateTime(),
                rs.getString("gabiste"), rs.getString("reference_externe"), rs.getString("numero_bon"),
                rs.getString("commentaire"), json.readValue(rs.getString("billetage"), new TypeReference<List<Ligne>>() { }),
                rs.getLong("montant"), rs.getString("statut"), rs.getString("saisi_par"),
                rs.getObject("saisi_le", OffsetDateTime.class), rs.getString("modifie_par"), rs.getString("decide_par"),
                rs.getObject("decide_le", OffsetDateTime.class), rs.getString("motif"), rs.getString("arrete"), null);
    }

    private Appro avecControle(Appro a) {
        return new Appro(a.id(), a.reference(), a.terminal(), a.libelleGab(), a.agence(), a.compteCaisse(), a.dateAppro(),
                a.gabiste(), a.referenceExterne(), a.numeroBon(), a.commentaire(), a.billetage(), a.montant(), a.statut(),
                a.saisiPar(), a.saisiLe(), a.modifiePar(), a.decidePar(), a.decideLe(), a.motif(), a.arrete(),
                controler(a.terminal(), a.dateAppro(), a.billetage()));
    }

    /** Compare le billetage saisi (regroupé par type de billet) aux billets ajoutés selon le journal du jour. */
    public ControleJournal controler(String terminal, LocalDateTime date, List<Ligne> billetage) {
        List<Integer> saisie = parType(billetage);
        JournalGab.Passage p = journal.passages(terminal, date.toLocalDate()).stream()
                .filter(x -> x.ajoutes() != null)
                .min(java.util.Comparator.comparing(x -> Math.abs(java.time.Duration.between(x.dateHeure(), date).toMinutes())))
                .orElse(null);
        if (p == null) {
            return new ControleJournal("ABSENT", null, null, saisie, null,
                    "Aucun ajout d'espèces dans les journaux intégrés pour ce GAB le " + date.toLocalDate() + ".");
        }
        List<Long> coupures = parametrage.coupuresParType(terminal).orElse(List.of(0L, 0L, 0L, 0L));
        long montantJournal = 0;
        for (int i = 0; i < 4; i++) {
            montantJournal += p.ajoutes().get(i) * coupures.get(i);
        }
        boolean egal = p.ajoutes().equals(saisie);
        String message = egal ? "Billetage conforme au journal (passage de " + p.dateHeure().toLocalTime() + ")."
                : "Écart avec le journal (passage de " + p.dateHeure().toLocalTime() + ") : " + ecarts(p.ajoutes(), saisie, coupures);
        return new ControleJournal(egal ? "CONCORDANT" : "ECART", p.dateHeure(), p.ajoutes(), saisie, montantJournal, message);
    }

    static List<Integer> parType(List<Ligne> billetage) {
        List<Integer> l = new ArrayList<>(List.of(0, 0, 0, 0));
        billetage.forEach(b -> l.set(b.type() - 1, l.get(b.type() - 1) + b.nombre()));
        return List.copyOf(l);
    }

    private static String ecarts(List<Integer> journal, List<Integer> saisie, List<Long> coupures) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (!journal.get(i).equals(saisie.get(i))) {
                l.add("billets de " + Montants.texte(coupures.get(i)) + " (type " + (i + 1) + ") : journal " + journal.get(i)
                        + ", saisie " + saisie.get(i));
            }
        }
        return String.join(" ; ", l);
    }

    private static String resume(Appro a) {
        return "GAB %s, %s, %s XOF (%s)".formatted(a.terminal(), a.dateAppro().toLocalDate(), Montants.texte(a.montant()),
                a.billetage().stream().filter(b -> b.nombre() > 0)
                        .map(b -> b.cassette() + " : " + b.nombre() + " × " + Montants.texte(b.coupure()))
                        .collect(Collectors.joining(", ")));
    }

    static String libelle(String statut) {
        return switch (statut) {
            case "VALIDE" -> "validé";
            case "ANNULE" -> "annulé";
            default -> "saisi";
        };
    }
}
