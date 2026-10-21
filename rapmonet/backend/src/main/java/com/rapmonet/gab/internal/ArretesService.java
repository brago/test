package com.rapmonet.gab.internal;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.gab.internal.ParametrageGabService.Cassette;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Arrêtés des GAB. Reprend l'écran « Arrêté GAB » de MONETICA V2 :
 * (0) montant de l'appro arrêté ; (1) billets en cassette et (2) rejets selon le journal (JGA) ;
 * (3) inventaire physique des cassettes et (4) des rejets ; (5) = (1) + (2) ; (6) = (3) + (4) ;
 * (7) transactions en attente ; (8) solde comptable du compte caisse du GAB.
 */
@Service
@Transactional
public class ArretesService {

    /** Compteurs du journal pour un type de billet : (1) billets en cassette, (2) rejets / retraits. */
    public record LigneJournal(int type, long coupure, int cassette, int rejet, long montant) {
    }

    /** Inventaire physique d'une cassette : (3) billets en cassette, (4) billets dans le bac de rejet. */
    public record LigneInventaire(String cassette, int type, long coupure, int nombre, int rejet, long montant) {
    }

    /**
     * @param approId    appro arrêté ; à défaut, le dernier appro validé non arrêté du GAB
     * @param journal    par type de billet (1 à 4) : nombre en cassette et rejets
     * @param inventaire par cassette (A à D) : nombre en cassette et rejets
     */
    public record Saisie(String terminal, Long approId, LocalDateTime dateArrete, String gabiste, String commentaire,
                         Map<Integer, Compte> journal, Map<String, Compte> inventaire, Long transactionsAttente,
                         Long soldeComptable) {
    }

    public record Compte(int nombre, int rejet) {
    }

    /**
     * @param ecartPhysiqueJournal    (6) − (5) : négatif = manquant
     * @param ecartPhysiqueComptable  (6) + (7) − (8) ; null tant que le solde comptable n'est pas saisi
     * @param retraitsJournal         retraits distribués selon les journaux intégrés entre l'appro et l'arrêté
     * @param soldeTheorique          (0) − retraits du journal
     * @param ecartJournalTheorique   (5) − solde théorique : billets non expliqués par les retraits enregistrés
     */
    public record Arrete(long id, String reference, String terminal, String libelleGab, String agence, String compteCaisse,
                         String compteManquant, String compteExcedent, long approId, String approReference,
                         LocalDateTime dateAppro, long montantAppro, LocalDateTime dateArrete, String gabiste,
                         String commentaire, List<LigneJournal> journal, List<LigneInventaire> inventaire,
                         long transactionsAttente, Long soldeComptable, long montantJournal, long montantPhysique,
                         long ecartPhysiqueJournal, Long ecartPhysiqueComptable, Long retraitsJournal, Long soldeTheorique,
                         Long ecartJournalTheorique, String statut, String saisiPar, OffsetDateTime saisiLe,
                         String modifiePar, String decidePar, OffsetDateTime decideLe, String motif,
                         JournalGab.Couverture couverture) {
    }

    /** Données pour préparer un arrêté : appros à arrêter, cassettes, passage du gabiste lu dans le journal. */
    public record Preparation(ParametrageGabService.Gab gab, List<Cassette> cassettes, List<Long> coupures,
                              List<ApprosService.Appro> approsNonArretes, JournalGab.Passage passage,
                              Long retraitsJournal, JournalGab.Couverture couverture) {
    }

    public record Critere(LocalDate du, LocalDate au, String terminal, String statut) {
    }

    private static final String SELECT = """
            select r.*, g.libelle as libelle_gab, g.agence, g.compte_caisse, g.compte_manquant, g.compte_excedent,
                a.reference as appro_reference, a.date_appro, a.montant as montant_appro
            from gab_arrete r join gab g on g.terminal_id = r.terminal_id join gab_appro a on a.id = r.appro_id
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final JournalAudit audit;
    private final ParametrageGabService parametrage;
    private final ApprosService appros;
    private final JournalGab journalGab;

    ArretesService(JdbcClient jdbc, JsonMapper json, JournalAudit audit, ParametrageGabService parametrage,
                   ApprosService appros, JournalGab journalGab) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.parametrage = parametrage;
        this.appros = appros;
        this.journalGab = journalGab;
    }

    // ------------------------------------------------------------------ préparation

    @Transactional(readOnly = true)
    public Preparation preparation(String terminal, LocalDateTime date) {
        ParametrageGabService.Gab gab = parametrage.gab(terminal);
        List<Cassette> cassettes = parametrage.profil(gab.profilCode()).cassettes();
        LocalDateTime moment = date == null ? LocalDateTime.now() : date;
        JournalGab.Passage passage = journalGab.passages(terminal, moment.toLocalDate()).stream()
                .filter(p -> p.cassettes() != null)
                .min(java.util.Comparator.comparing(p -> Math.abs(java.time.Duration.between(p.dateHeure(), moment).toMinutes())))
                .orElse(null);
        List<ApprosService.Appro> ouverts = appros.nonArretes(terminal);
        Long retraits = null;
        JournalGab.Couverture couverture = null;
        if (!ouverts.isEmpty()) {
            LocalDateTime fin = passage != null ? passage.dateHeure() : moment;
            retraits = journalGab.montantDistribue(terminal, ouverts.getFirst().dateAppro(), fin);
            couverture = journalGab.couverture(terminal, ouverts.getFirst().dateAppro().toLocalDate(), fin.toLocalDate());
        }
        return new Preparation(gab, cassettes, ParametrageGabService.coupuresParType(cassettes), ouverts, passage, retraits,
                couverture);
    }

    // ------------------------------------------------------------------ saisie

    public Arrete creer(Saisie s, String utilisateur) {
        Valide v = valider(s, null);
        long id;
        try {
            id = jdbc.sql("""
                            insert into gab_arrete (reference, terminal_id, appro_id, date_arrete, gabiste, commentaire, journal,
                                inventaire, transactions_attente, solde_comptable, montant_journal, montant_physique,
                                retraits_journal, statut, saisi_par, modifie_par)
                            values (lpad(nextval('gab_arrete_reference')::text, 7, '0'), ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb,
                                ?, ?, ?, ?, ?, 'SAISI', ?, ?)
                            returning id""")
                    .params(v.appro.terminal(), v.appro.id(), Timestamp.valueOf(s.dateArrete()), v.gabiste,
                            ParametrageGabService.vide(s.commentaire()), json.writeValueAsString(v.journal),
                            json.writeValueAsString(v.inventaire), v.attente, s.soldeComptable(), v.montantJournal,
                            v.montantPhysique, v.retraits, utilisateur, utilisateur)
                    .query(Long.class).single();
        } catch (DuplicateKeyException e) {
            throw new RegleMetierException("L'approvisionnement " + v.appro.reference() + " a déjà un arrêté.");
        }
        Arrete a = arrete(id);
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_ARRETE_SAISIE, "Arrêté " + a.reference(), resume(a), true);
        return a;
    }

    public Arrete modifier(long id, Saisie s, String utilisateur) {
        Arrete avant = arrete(id);
        if (!avant.statut().equals("SAISI")) {
            throw new RegleMetierException("Un arrêté " + ApprosService.libelle(avant.statut()) + " ne peut plus être modifié.");
        }
        Valide v = valider(s, avant);
        try {
            jdbc.sql("""
                            update gab_arrete set appro_id = ?, date_arrete = ?, gabiste = ?, commentaire = ?, journal = ?::jsonb,
                                inventaire = ?::jsonb, transactions_attente = ?, solde_comptable = ?, montant_journal = ?,
                                montant_physique = ?, retraits_journal = ?, modifie_par = ?, modifie_le = now()
                            where id = ? and statut = 'SAISI'""")
                    .params(v.appro.id(), Timestamp.valueOf(s.dateArrete()), v.gabiste, ParametrageGabService.vide(s.commentaire()),
                            json.writeValueAsString(v.journal), json.writeValueAsString(v.inventaire), v.attente,
                            s.soldeComptable(), v.montantJournal, v.montantPhysique, v.retraits, utilisateur, id)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new RegleMetierException("L'approvisionnement " + v.appro.reference() + " a déjà un arrêté.");
        }
        Arrete a = arrete(id);
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_ARRETE_SAISIE, "Arrêté " + a.reference(), "Modification : " + resume(a), true);
        return a;
    }

    private record Valide(ApprosService.Appro appro, String gabiste, List<LigneJournal> journal,
                          List<LigneInventaire> inventaire, long attente, long montantJournal, long montantPhysique,
                          Long retraits) {
    }

    private Valide valider(Saisie s, Arrete existant) {
        String terminal = ParametrageGabService.obligatoire(s.terminal(), "Le GAB");
        ParametrageGabService.Gab gab = parametrage.gab(terminal);
        if (s.dateArrete() == null) {
            throw new RegleMetierException("La date de l'arrêté est obligatoire.");
        }
        if (s.dateArrete().isAfter(LocalDateTime.now().plusHours(1))) {
            throw new RegleMetierException("La date de l'arrêté ne peut pas être dans le futur.");
        }
        ApprosService.Appro appro;
        if (s.approId() != null) {
            appro = appros.appro(s.approId());
        } else if (existant != null) {
            appro = appros.appro(existant.approId());
        } else {
            appro = appros.nonArretes(terminal).stream().findFirst().orElseThrow(() -> new RegleMetierException(
                    "Aucun approvisionnement validé à arrêter pour le GAB " + terminal + "."));
        }
        if (!appro.terminal().equals(terminal)) {
            throw new RegleMetierException("L'approvisionnement " + appro.reference() + " concerne un autre GAB.");
        }
        if (!appro.statut().equals("VALIDE")) {
            throw new RegleMetierException("Seul un approvisionnement validé peut être arrêté (l'appro " + appro.reference()
                    + " est " + ApprosService.libelle(appro.statut()) + ").");
        }
        if (!s.dateArrete().isAfter(appro.dateAppro())) {
            throw new RegleMetierException("L'arrêté doit être postérieur à l'approvisionnement du " + appro.dateAppro() + ".");
        }
        String gabiste = ParametrageGabService.obligatoire(s.gabiste(), "Le gabiste");
        List<Cassette> cassettes = parametrage.profil(gab.profilCode()).cassettes();
        List<LigneJournal> journal = journal(cassettes, s.journal());
        List<LigneInventaire> inventaire = inventaire(cassettes, s.inventaire());
        long attente = s.transactionsAttente() == null ? 0 : s.transactionsAttente();
        if (attente < 0 || (s.soldeComptable() != null && s.soldeComptable() < 0)) {
            throw new RegleMetierException("Les montants ne peuvent pas être négatifs.");
        }
        long retraits = journalGab.montantDistribue(terminal, appro.dateAppro(), s.dateArrete());
        return new Valide(appro, gabiste, journal, inventaire, attente,
                journal.stream().mapToLong(LigneJournal::montant).sum(),
                inventaire.stream().mapToLong(LigneInventaire::montant).sum(), retraits);
    }

    /** Une ligne par type de billet chargé dans le GAB (d'après les cassettes du profil). */
    static List<LigneJournal> journal(List<Cassette> cassettes, Map<Integer, Compte> saisis) {
        Map<Integer, Long> types = new LinkedHashMap<>();
        cassettes.stream().sorted(java.util.Comparator.comparingInt(Cassette::type))
                .forEach(c -> types.putIfAbsent(c.type(), c.coupure()));
        Map<Integer, Compte> s = saisis == null ? Map.of() : saisis;
        List<LigneJournal> l = new ArrayList<>();
        types.forEach((type, coupure) -> {
            Compte c = s.getOrDefault(type, new Compte(0, 0));
            positif(c, "Type " + type);
            l.add(new LigneJournal(type, coupure, c.nombre(), c.rejet(), (long) (c.nombre() + c.rejet()) * coupure));
        });
        return l;
    }

    static List<LigneInventaire> inventaire(List<Cassette> cassettes, Map<String, Compte> saisis) {
        Map<String, Compte> s = saisis == null ? Map.of() : saisis;
        List<LigneInventaire> l = new ArrayList<>();
        for (Cassette c : cassettes) {
            Compte n = s.getOrDefault(c.cassette(), new Compte(0, 0));
            positif(n, "Cassette " + c.cassette());
            l.add(new LigneInventaire(c.cassette(), c.type(), c.coupure(), n.nombre(), n.rejet(),
                    (long) (n.nombre() + n.rejet()) * c.coupure()));
        }
        return l;
    }

    private static void positif(Compte c, String libelle) {
        if (c == null || c.nombre() < 0 || c.rejet() < 0) {
            throw new RegleMetierException(libelle + " : nombre de billets négatif.");
        }
    }

    // ------------------------------------------------------------------ validation à quatre yeux

    public Arrete valider(long id, String utilisateur) {
        Arrete a = arrete(id);
        if (!a.statut().equals("SAISI")) {
            throw new RegleMetierException("Cet arrêté est déjà " + ApprosService.libelle(a.statut()) + ".");
        }
        if (utilisateur.equalsIgnoreCase(a.saisiPar()) || utilisateur.equalsIgnoreCase(a.modifiePar())) {
            throw new RegleMetierException("Validation à quatre yeux : l'arrêté doit être validé par une autre personne "
                    + "que celle qui l'a saisi ou modifié.");
        }
        jdbc.sql("update gab_arrete set statut = 'VALIDE', decide_par = ?, decide_le = now() where id = ? and statut = 'SAISI'")
                .params(utilisateur, id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_ARRETE_VALIDATION, "Arrêté " + a.reference(), resume(a), true);
        return arrete(id);
    }

    public Arrete annuler(long id, String motif, String utilisateur) {
        Arrete a = arrete(id);
        String m = ParametrageGabService.obligatoire(motif, "Le motif de l'annulation");
        if (a.statut().equals("ANNULE")) {
            throw new RegleMetierException("Cet arrêté est déjà annulé.");
        }
        jdbc.sql("update gab_arrete set statut = 'ANNULE', decide_par = ?, decide_le = now(), motif = ? where id = ?")
                .params(utilisateur, m, id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.GAB_ARRETE_ANNULATION, "Arrêté " + a.reference(), m, true);
        return arrete(id);
    }

    // ------------------------------------------------------------------ consultation

    @Transactional(readOnly = true)
    public Arrete arrete(long id) {
        Arrete a = jdbc.sql(SELECT + " where r.id = ?").param(id).query((rs, n) -> arrete(rs)).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Arrêté introuvable."));
        return avecCouverture(a);
    }

    /** Couverture des journaux entre l'appro et l'arrêté (le solde théorique n'est fiable que si elle est complète). */
    private Arrete avecCouverture(Arrete a) {
        return new Arrete(a.id(), a.reference(), a.terminal(), a.libelleGab(), a.agence(), a.compteCaisse(), a.compteManquant(),
                a.compteExcedent(), a.approId(), a.approReference(), a.dateAppro(), a.montantAppro(), a.dateArrete(),
                a.gabiste(), a.commentaire(), a.journal(), a.inventaire(), a.transactionsAttente(), a.soldeComptable(),
                a.montantJournal(), a.montantPhysique(), a.ecartPhysiqueJournal(), a.ecartPhysiqueComptable(),
                a.retraitsJournal(), a.soldeTheorique(), a.ecartJournalTheorique(), a.statut(), a.saisiPar(), a.saisiLe(),
                a.modifiePar(), a.decidePar(), a.decideLe(), a.motif(),
                journalGab.couverture(a.terminal(), a.dateAppro().toLocalDate(), a.dateArrete().toLocalDate()));
    }

    @Transactional(readOnly = true)
    public PageResultat<Arrete> rechercher(Critere c, int page, int taille) {
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        long total = jdbc.sql("select count(*) from gab_arrete r" + where).params(params).query(Long.class).single();
        List<Object> p = new ArrayList<>(params);
        p.add(taille);
        p.add((long) page * taille);
        return new PageResultat<>(jdbc.sql(SELECT + where + " order by r.date_arrete desc, r.id desc limit ? offset ?").params(p)
                .query((rs, n) -> arrete(rs)).list(), total, page, taille);
    }

    @Transactional(readOnly = true)
    public void parcourir(Critere c, Consumer<Arrete> consommateur) {
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        jdbc.sql(SELECT + where + " order by r.date_arrete desc, r.id desc").params(params).query((rs, n) -> arrete(rs)).list()
                .forEach(consommateur);
    }

    private static String where(Critere c, List<Object> params) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (c.du() != null) {
            w.append(" and r.date_arrete >= ?");
            params.add(Timestamp.valueOf(c.du().atStartOfDay()));
        }
        if (c.au() != null) {
            w.append(" and r.date_arrete < ?");
            params.add(Timestamp.valueOf(c.au().plusDays(1).atStartOfDay()));
        }
        if (c.terminal() != null && !c.terminal().isBlank()) {
            w.append(" and r.terminal_id = ?");
            params.add(c.terminal());
        }
        if (c.statut() != null && !c.statut().isBlank()) {
            w.append(" and r.statut = ?");
            params.add(c.statut());
        }
        return w.toString();
    }

    private Arrete arrete(ResultSet rs) throws SQLException {
        long appro = rs.getLong("montant_appro");
        long journal = rs.getLong("montant_journal");
        long physique = rs.getLong("montant_physique");
        long attente = rs.getLong("transactions_attente");
        Long comptable = (Long) rs.getObject("solde_comptable");
        Long retraits = (Long) rs.getObject("retraits_journal");
        Long theorique = retraits == null ? null : appro - retraits;
        return new Arrete(rs.getLong("id"), rs.getString("reference"), rs.getString("terminal_id"), rs.getString("libelle_gab"),
                rs.getString("agence"), rs.getString("compte_caisse"), rs.getString("compte_manquant"),
                rs.getString("compte_excedent"), rs.getLong("appro_id"), rs.getString("appro_reference"),
                rs.getTimestamp("date_appro").toLocalDateTime(), appro, rs.getTimestamp("date_arrete").toLocalDateTime(),
                rs.getString("gabiste"), rs.getString("commentaire"),
                json.readValue(rs.getString("journal"), new TypeReference<List<LigneJournal>>() { }),
                json.readValue(rs.getString("inventaire"), new TypeReference<List<LigneInventaire>>() { }),
                attente, comptable, journal, physique, physique - journal,
                comptable == null ? null : physique + attente - comptable,
                retraits, theorique, theorique == null ? null : journal - theorique,
                rs.getString("statut"), rs.getString("saisi_par"), rs.getObject("saisi_le", OffsetDateTime.class),
                rs.getString("modifie_par"), rs.getString("decide_par"), rs.getObject("decide_le", OffsetDateTime.class),
                rs.getString("motif"), null);
    }

    private static String resume(Arrete a) {
        return "GAB %s, %s (appro %s) : journal %s, physique %s, écart %s".formatted(a.terminal(), a.dateArrete().toLocalDate(),
                a.approReference(), Montants.texte(a.montantJournal()), Montants.texte(a.montantPhysique()),
                Montants.texte(a.ecartPhysiqueJournal()));
    }
}
