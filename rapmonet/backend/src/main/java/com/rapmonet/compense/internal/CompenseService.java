package com.rapmonet.compense.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.compense.internal.CommercantsService.Commercant;
import com.rapmonet.compense.internal.CommercantsService.Parametres;
import com.rapmonet.ingestion.AchatsAcquis;
import com.rapmonet.ingestion.AchatsAcquis.Achat;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Traitement de compense TPE d'une journée LIS : une ligne par achat acquis (TC 49), compte à débiter selon
 * le réseau, compte du commerçant à créditer, frais (CCI), commission HT et TAF ; écritures de la macro comptable.
 */
@Service
@Transactional
public class CompenseService {

    /**
     * Achat à compenser.
     *
     * @param onUs      achat GIM sans frais d'interchange : carte de la banque (compte EMIS ONUS)
     * @param anomalie  motif qui empêche de compenser l'achat (commerçant inconnu, taux nul…), sinon vide
     */
    public record Ligne(long achatId, String reseau, boolean onUs, String terminal, String commercant, String compte,
                        String compteDebit, String carte, String autorisation, String rrn, String arn, LocalDate dateTransaction,
                        String heureTransaction, long montant, BigDecimal taux, BigDecimal cci, long frais, long commission,
                        long taf, String anomalie) {
    }

    /** Totaux d'une catégorie : VISA, MASTERCARD, GIM ACQ, GIM ONUS. */
    public record TotalReseau(String categorie, long nombre, long montant, long frais, long commission, long taf,
                              String compteDebit, String compteCredit) {
    }

    /** Écriture de la macro comptable (format d'import Amplitude). */
    public record Ecriture(String compte, LocalDate dateOperation, LocalDate dateValeur, long montant, String libelle) {
    }

    /** Solde d'un compte mouvementé par les écritures. */
    public record CompteMouvemente(String compte, String libelle, long debit, long credit) {
    }

    /**
     * @param dejaCompenses achats de la journée déjà compris dans un autre traitement
     * @param anomalies     problèmes bloquants : comptes généraux manquants, commerçants inconnus…
     */
    public record Apercu(LocalDate journee, List<Ligne> lignes, List<TotalReseau> totaux, int dejaCompenses,
                         List<String> anomalies) {
    }

    public record Traitement(long id, String reference, LocalDate journee, int nbTransactions, long montant,
                             long commission, long taf, String statut, String creePar, OffsetDateTime creeLe,
                             String decidePar, OffsetDateTime decideLe, String motif, Parametres parametres,
                             List<Ligne> lignes, List<TotalReseau> totaux, List<Ecriture> ecritures,
                             List<CompteMouvemente> comptes) {
    }

    private static final String COMMERCANTS = "*commercants*";
    private static final String[] MOIS = {"Janv", "Févr", "Mars", "Avril", "Mai", "Juin", "Juil", "Août", "Sept", "Oct",
            "Nov", "Déc"};
    private static final String[] JOURS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final AchatsAcquis achats;
    private final CommercantsService commercants;
    private final JournalAudit audit;

    CompenseService(JdbcClient jdbc, JsonMapper json, AchatsAcquis achats, CommercantsService commercants, JournalAudit audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.achats = achats;
        this.commercants = commercants;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ préparation

    @Transactional(readOnly = true)
    public List<LocalDate> journees() {
        return achats.journees();
    }

    @Transactional(readOnly = true)
    public Apercu apercu(LocalDate journee) {
        Parametres p = commercants.parametres();
        Map<String, Commercant> refs = commercants.parTerminal();
        List<Achat> tous = achats.achats(journee);
        Set<Long> compenses = new HashSet<>(jdbc.sql("select lis_transaction_id from compense_ligne where actif")
                .query(Long.class).list());
        List<Ligne> lignes = new ArrayList<>();
        int deja = 0;
        for (Achat a : tous) {
            if (compenses.contains(a.id())) {
                deja++;
                continue;
            }
            lignes.add(ligne(a, refs, p));
        }
        List<String> anomalies = new ArrayList<>();
        List<String> manquants = CommercantsService.comptesManquants(p);
        if (!manquants.isEmpty()) {
            anomalies.add("Comptes généraux à renseigner (Commerçants et comptes) : " + String.join(", ", manquants) + ".");
        }
        Map<String, String> inconnus = new TreeMap<>();
        lignes.stream().filter(l -> l.anomalie() != null).forEach(l -> inconnus.putIfAbsent(l.terminal(), l.anomalie()));
        inconnus.forEach((t, m) -> anomalies.add("Terminal " + t + " : " + m));
        return new Apercu(journee, lignes, totaux(lignes, p), deja, anomalies);
    }

    private static Ligne ligne(Achat a, Map<String, Commercant> refs, Parametres p) {
        String terminal = CommercantsService.normaliserTerminal(a.terminal());
        Commercant c = terminal == null ? null : refs.get(terminal);
        boolean onUs = "GIM".equals(a.reseau()) && (a.interchange() == null || a.interchange().signum() == 0);
        String compteDebit = switch (a.reseau()) {
            case "VISA" -> p.compteAcqVisa();
            case "MASTERCARD" -> p.compteAcqMci();
            default -> onUs ? p.compteEmisOnus() : p.compteAcqGim();
        };
        String anomalie = null;
        BigDecimal taux = BigDecimal.ZERO;
        if (c == null) {
            anomalie = "commerçant absent du référentiel (" + Objects.toString(a.marchand(), "nom inconnu") + ")";
        } else if (!c.actif()) {
            anomalie = "commerçant désactivé (" + c.nom() + ")";
        } else {
            taux = c.taux(a.reseau());
            if (taux.signum() == 0) {
                anomalie = "taux " + libelleReseau(a.reseau()) + " nul pour " + c.nom();
            }
        }
        if (a.montant() <= 0 && anomalie == null) {
            anomalie = "montant nul";
        }
        CalculFrais f = CalculFrais.calculer(a.montant(), taux, p.tauxTaf());
        return new Ligne(a.id(), a.reseau(), onUs, terminal, c != null ? c.nom() : Objects.toString(a.marchand(), ""),
                c != null ? c.compte() : "", Objects.toString(compteDebit, ""), a.carte(), a.autorisation(), a.rrn(), a.arn(),
                a.dateTransaction(), a.heureTransaction(), a.montant(), taux, f.cci(), f.frais(), f.commission(), f.taf(),
                anomalie);
    }

    static String libelleReseau(String reseau) {
        return switch (reseau) {
            case "VISA" -> "VISA";
            case "MASTERCARD" -> "MCI";
            default -> "GIM";
        };
    }

    private static List<TotalReseau> totaux(List<Ligne> lignes, Parametres p) {
        List<TotalReseau> l = new ArrayList<>();
        l.add(total("VISA", lignes.stream().filter(x -> x.reseau().equals("VISA")).toList(), p.compteAcqVisa(),
                p.compteCompensationVisaMci()));
        l.add(total("MASTERCARD", lignes.stream().filter(x -> x.reseau().equals("MASTERCARD")).toList(), p.compteAcqMci(),
                p.compteCompensationVisaMci()));
        l.add(total("GIM ACQ", lignes.stream().filter(x -> x.reseau().equals("GIM") && !x.onUs()).toList(), p.compteAcqGim(),
                p.compteCompensationGim()));
        l.add(total("GIM ONUS", lignes.stream().filter(x -> x.reseau().equals("GIM") && x.onUs()).toList(), p.compteEmisOnus(),
                p.compteCompensationGim()));
        return l;
    }

    private static TotalReseau total(String categorie, List<Ligne> l, String debit, String credit) {
        return new TotalReseau(categorie, l.size(), l.stream().mapToLong(Ligne::montant).sum(),
                l.stream().mapToLong(Ligne::frais).sum(), l.stream().mapToLong(Ligne::commission).sum(),
                l.stream().mapToLong(Ligne::taf).sum(), debit, credit);
    }

    // ------------------------------------------------------------------ traitement

    public Traitement creer(LocalDate journee, String utilisateur) {
        Apercu a = apercu(journee);
        if (!a.anomalies().isEmpty()) {
            throw new RegleMetierException("Compense impossible : " + String.join(" ; ", a.anomalies()));
        }
        if (a.lignes().isEmpty()) {
            throw new RegleMetierException(a.dejaCompenses() > 0
                    ? "Tous les achats de la journée sont déjà compensés."
                    : "Aucun achat acquis (TC 49) dans les fichiers LIS du " + journee + ".");
        }
        Parametres p = commercants.parametres();
        long id = jdbc.sql("""
                        insert into compense_traitement (reference, journee, parametres, nb_transactions, montant, commission,
                            taf, statut, cree_par)
                        values (lpad(nextval('compense_reference')::text, 7, '0'), ?, ?::jsonb, ?, ?, ?, ?, 'SAISI', ?)
                        returning id""")
                .params(journee, json.writeValueAsString(p), a.lignes().size(),
                        a.lignes().stream().mapToLong(Ligne::montant).sum(), a.lignes().stream().mapToLong(Ligne::commission).sum(),
                        a.lignes().stream().mapToLong(Ligne::taf).sum(), utilisateur)
                .query(Long.class).single();
        try {
            for (Ligne l : a.lignes()) {
                jdbc.sql("""
                                insert into compense_ligne (traitement_id, lis_transaction_id, reseau, on_us, terminal_id, commercant,
                                    compte_commercant, compte_debit, carte, autorisation, rrn, arn, date_transaction,
                                    heure_transaction, montant, taux, cci, frais, commission, taf)
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                        .params(id, l.achatId(), l.reseau(), l.onUs(), l.terminal(), l.commercant(), l.compte(), l.compteDebit(),
                                l.carte(), l.autorisation(), l.rrn(), l.arn(), l.dateTransaction(), l.heureTransaction(),
                                l.montant(), l.taux(), l.cci(), l.frais(), l.commission(), l.taf())
                        .update();
            }
        } catch (DuplicateKeyException e) {
            throw new RegleMetierException("Un autre traitement vient de compenser ces achats : actualisez.");
        }
        Traitement t = traitement(id);
        audit.enregistrerPour(utilisateur, ActionsAudit.COMPENSE_CREATION, "Compense TPE " + t.reference(), resume(t), true);
        return t;
    }

    public Traitement valider(long id, String utilisateur) {
        Traitement t = traitement(id);
        if (!t.statut().equals("SAISI")) {
            throw new RegleMetierException("Ce traitement est déjà " + (t.statut().equals("VALIDE") ? "validé" : "annulé") + ".");
        }
        if (utilisateur.equalsIgnoreCase(t.creePar())) {
            throw new RegleMetierException("Validation à quatre yeux : le traitement doit être validé par une autre personne "
                    + "que celle qui l'a préparé.");
        }
        jdbc.sql("update compense_traitement set statut = 'VALIDE', decide_par = ?, decide_le = now() where id = ? and statut = 'SAISI'")
                .params(utilisateur, id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.COMPENSE_VALIDATION, "Compense TPE " + t.reference(), resume(t), true);
        return traitement(id);
    }

    public Traitement annuler(long id, String motif, String utilisateur) {
        Traitement t = traitement(id);
        if (motif == null || motif.isBlank()) {
            throw new RegleMetierException("Le motif de l'annulation est obligatoire.");
        }
        if (t.statut().equals("ANNULE")) {
            throw new RegleMetierException("Ce traitement est déjà annulé.");
        }
        jdbc.sql("update compense_traitement set statut = 'ANNULE', decide_par = ?, decide_le = now(), motif = ? where id = ?")
                .params(utilisateur, motif.strip(), id).update();
        // Les achats redeviennent compensables.
        jdbc.sql("update compense_ligne set actif = false where traitement_id = ?").param(id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.COMPENSE_ANNULATION, "Compense TPE " + t.reference(), motif, true);
        return traitement(id);
    }

    // ------------------------------------------------------------------ consultation

    @Transactional(readOnly = true)
    public List<Traitement> traitements(String statut) {
        String where = statut == null || statut.isBlank() ? "" : " where statut = ?";
        var requete = jdbc.sql("select * from compense_traitement" + where + " order by journee desc, id desc limit 200");
        if (!where.isEmpty()) {
            requete = requete.param(statut);
        }
        return requete.query((rs, n) -> entete(rs)).list();
    }

    @Transactional(readOnly = true)
    public Traitement traitement(long id) {
        Traitement t = jdbc.sql("select * from compense_traitement where id = ?").param(id).query((rs, n) -> entete(rs))
                .optional().orElseThrow(() -> new RessourceIntrouvableException("Traitement de compense introuvable."));
        List<Ligne> lignes = jdbc.sql("select * from compense_ligne where traitement_id = ? order by reseau, id").param(id)
                .query((rs, n) -> new Ligne(rs.getLong("lis_transaction_id"), rs.getString("reseau"), rs.getBoolean("on_us"),
                        rs.getString("terminal_id"), rs.getString("commercant"), rs.getString("compte_commercant"),
                        rs.getString("compte_debit"), rs.getString("carte"), rs.getString("autorisation"), rs.getString("rrn"),
                        rs.getString("arn"), rs.getObject("date_transaction", LocalDate.class), rs.getString("heure_transaction"),
                        rs.getLong("montant"), rs.getBigDecimal("taux"), rs.getBigDecimal("cci"), rs.getLong("frais"),
                        rs.getLong("commission"), rs.getLong("taf"), null))
                .list();
        Parametres p = t.parametres();
        List<Ecriture> ecritures = ecritures(t.journee(), lignes, p);
        return new Traitement(t.id(), t.reference(), t.journee(), t.nbTransactions(), t.montant(), t.commission(), t.taf(),
                t.statut(), t.creePar(), t.creeLe(), t.decidePar(), t.decideLe(), t.motif(), p, lignes, totaux(lignes, p),
                ecritures, comptes(ecritures, p));
    }

    private Traitement entete(ResultSet rs) throws SQLException {
        return new Traitement(rs.getLong("id"), rs.getString("reference"), rs.getObject("journee", LocalDate.class),
                rs.getInt("nb_transactions"), rs.getLong("montant"), rs.getLong("commission"), rs.getLong("taf"),
                rs.getString("statut"), rs.getString("cree_par"), rs.getObject("cree_le", OffsetDateTime.class),
                rs.getString("decide_par"), rs.getObject("decide_le", OffsetDateTime.class), rs.getString("motif"),
                json.readValue(rs.getString("parametres"), Parametres.class), null, null, null, null);
    }

    // ------------------------------------------------------------------ écritures (macro comptable)

    /** Libellé de date de la macro : « 07 Mai Jeudi ». */
    static String libelleDate(LocalDate d) {
        return "%02d %s %s".formatted(d.getDayOfMonth(), MOIS[d.getMonthValue() - 1], JOURS[d.getDayOfWeek().getValue() - 1]);
    }

    /**
     * Écritures, dans l'ordre de la macro : pour chaque achat, débit du compte réseau et crédit du commerçant
     * (montant), débit du commerçant (frais TTC), crédit commission (HT) et TAF ; puis récapitulatif à J+1 :
     * compensation VISA-MCI, compensation GIM, transfert des commissions. Les lignes de montant nul sont omises.
     */
    static List<Ecriture> ecritures(LocalDate journee, List<Ligne> lignes, Parametres p) {
        String libDate = libelleDate(journee);
        List<Ecriture> e = new ArrayList<>();
        for (Ligne l : lignes) {
            String paie = "Paiement Transactions du " + libDate + " TPE " + l.commercant() + " REF " + Objects.toString(l.autorisation(), "");
            String frais = "Frais Cssions Transactions du " + libDate + " TPE " + l.commercant() + " REF " + Objects.toString(l.autorisation(), "");
            LocalDate d = l.dateTransaction();
            ajouter(e, l.compteDebit(), d, -l.montant(), paie);
            ajouter(e, l.compte(), d, l.montant(), paie);
            ajouter(e, l.compte(), d, -l.frais(), frais);
            ajouter(e, p.compteCommission(), d, l.commission(), frais);
            ajouter(e, p.compteTaf(), d, l.taf(), "TAF " + frais);
        }
        LocalDate recap = journee.plusDays(1);
        long visa = somme(lignes, "VISA", null);
        long mci = somme(lignes, "MASTERCARD", null);
        long gimAcq = somme(lignes, "GIM", false);
        long gimOnus = somme(lignes, "GIM", true);
        long commission = lignes.stream().mapToLong(Ligne::commission).sum();
        ajouter(e, p.compteCompensationVisaMci(), recap, -(visa + mci), "ACTIVITE ACQUISITION ACHAT VISA-MCI TRANSACTIONS TPE DU " + libDate);
        ajouter(e, p.compteAcqVisa(), recap, visa, "ACTIVITE ACQUISITION ACHAT VISA TRANSACTIONS TPE DU " + libDate);
        ajouter(e, p.compteAcqMci(), recap, mci, "ACTIVITE ACQUISITION ACHAT MCI TRANSACTIONS TPE DU " + libDate);
        ajouter(e, p.compteCompensationGim(), recap, -(gimAcq + gimOnus), "ACTIVITE ACQUISITION ACHAT GIM TRANSACTIONS TPE DU " + libDate);
        ajouter(e, p.compteAcqGim(), recap, gimAcq, "ACTIVITE ACQUISITION ACHAT GIM TRANSACTIONS TPE DU " + libDate);
        ajouter(e, p.compteEmisOnus(), recap, gimOnus, "ACTIVITE ACQUISITION ACHAT GIM TRANSACTIONS TPE DU " + libDate);
        ajouter(e, p.compteCommission(), recap, -commission, "FRAIS ACTIVITE ACQUISITION ACHAT DU " + libDate);
        ajouter(e, p.compteCompensationGim(), recap, commission, "FRAIS ACTIVITE ACQUISITION ACHAT DU " + libDate);
        return e;
    }

    private static void ajouter(List<Ecriture> e, String compte, LocalDate date, long montant, String libelle) {
        if (montant != 0) {
            e.add(new Ecriture(compte, date, date, montant, libelle));
        }
    }

    private static long somme(List<Ligne> lignes, String reseau, Boolean onUs) {
        return lignes.stream().filter(l -> l.reseau().equals(reseau) && (onUs == null || l.onUs() == onUs))
                .mapToLong(Ligne::montant).sum();
    }

    /** Débit / crédit de chaque compte ; la somme des écritures est nulle (pièce équilibrée). */
    static List<CompteMouvemente> comptes(List<Ecriture> ecritures, Parametres p) {
        Map<String, String> libelles = new LinkedHashMap<>();
        libelles.put(p.compteAcqVisa(), "Acquisitions VISA");
        libelles.put(p.compteAcqMci(), "Acquisitions MCI");
        libelles.put(p.compteAcqGim(), "Acquisitions GIM");
        libelles.put(p.compteEmisOnus(), "Acquisitions GIM on-us");
        libelles.put(p.compteCompensationVisaMci(), "Compensation VISA / MCI");
        libelles.put(p.compteCompensationGim(), "Compensation GIM");
        libelles.put(p.compteCommission(), "Commissions banque");
        libelles.put(p.compteTaf(), "TAF");
        Map<String, long[]> m = new LinkedHashMap<>();
        libelles.keySet().forEach(k -> m.put(k, new long[2]));
        for (Ecriture e : ecritures) {
            String cle = libelles.containsKey(e.compte()) ? e.compte() : COMMERCANTS;
            long[] v = m.computeIfAbsent(cle, k -> new long[2]);
            if (e.montant() < 0) {
                v[0] -= e.montant();
            } else {
                v[1] += e.montant();
            }
        }
        List<CompteMouvemente> l = new ArrayList<>();
        m.forEach((compte, v) -> {
            if (v[0] != 0 || v[1] != 0) {
                l.add(compte.equals(COMMERCANTS) ? new CompteMouvemente("", "Comptes des commerçants", v[0], v[1])
                        : new CompteMouvemente(compte, libelles.get(compte), v[0], v[1]));
            }
        });
        return l;
    }

    private static String resume(Traitement t) {
        return "Journée %s : %d achat(s), %s XOF, commission %s, TAF %s".formatted(
                t.journee().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), t.nbTransactions(), t.montant(),
                t.commission(), t.taf());
    }
}
