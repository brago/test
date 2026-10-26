package com.rapmonet.chargeback.internal;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.gab.BinsBanque;
import com.rapmonet.ingestion.ConsultationJournaux;
import com.rapmonet.ingestion.LitigesLis;
import com.rapmonet.ingestion.LitigesLis.OperationLitige;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Suivi des chargebacks GIM : un dossier par ARN, cycle de vie relu dans les fichiers LIS intégrés
 * (première présentation, chargeback, représentation, second chargeback, redressements), délai de
 * représentation, régularisation et archivage. Côté acquisition, le journal du GAB dit si les billets ont été pris.
 */
@Service
@Transactional
public class ChargebacksService implements com.rapmonet.chargeback.DossiersPorteur {

    public enum Sens { EMISSION, ACQUISITION }

    /** Une ligne du cycle de vie. @param alerte la situation demande une action ou une vérification */
    public record LigneCycle(String etape, String situation, LocalDate date, boolean alerte) {
    }

    /** Opération LIS du cycle. */
    public record Operation(LocalDate journee, String fichier, String tc, String libelle, String usage, Long montant,
                            String motif, String message) {
    }

    /**
     * @param etat               EN_ATTENTE, REPRESENTE, SECOND_CHARGEBACK, ANNULE, DELAI_ECOULE
     * @param joursEcoules       jours depuis la journée LIS du chargeback
     * @param echeance           fin du délai de représentation
     */
    public record Dossier(long id, Sens sens, String arn, String tc, String libelleTc, String rrn, String carte,
                          String terminal, String marchand, LocalDate dateTransaction, String heureTransaction,
                          String autorisation, Long montant, String devise, String motif, String referenceChargeback,
                          LocalDate journeeChargeback, String etat, String situation, long joursEcoules,
                          LocalDate echeance, String regularisation, String commentaire, String regularisePar,
                          OffsetDateTime regulariseLe, boolean archive, List<LigneCycle> cycle,
                          List<Operation> operations) {
    }

    public record Critere(Sens sens, LocalDate du, LocalDate au, String reference, String regularisation,
                          boolean archives) {
    }

    /**
     * Retrait correspondant dans le journal du GAB (acquisition).
     *
     * @param conclusion aide à la décision : billets pris (représenter) ou non (chargeback fondé)
     */
    public record JournalGab(String conclusion, boolean billetsPris, List<ConsultationJournaux.Operation> operations,
                             ConsultationJournaux.Session session, String recherche) {
    }

    private static final Map<String, String> LIBELLES_TC = Map.ofEntries(
            Map.entry("05", "Achat"), Map.entry("06", "Crédit voucher"), Map.entry("07", "Retrait"),
            Map.entry("15", "Chargeback achat"), Map.entry("16", "Chargeback crédit voucher"),
            Map.entry("17", "Chargeback retrait"), Map.entry("25", "Redressement achat"),
            Map.entry("26", "Redressement crédit voucher"), Map.entry("27", "Redressement retrait"),
            Map.entry("35", "Redressement chargeback achat"), Map.entry("36", "Redressement chargeback crédit voucher"),
            Map.entry("37", "Redressement chargeback retrait"));
    private static final Set<String> PRESENTATIONS = Set.of("05", "06", "07");
    private static final Set<String> CHARGEBACKS = Set.of("15", "16", "17");
    private static final Set<String> REDRESSEMENTS_CHARGEBACK = Set.of("35", "36", "37");
    private static final Set<String> REDRESSEMENTS_PRESENTATION = Set.of("25", "26", "27");
    private static final DateTimeFormatter JJ = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcClient jdbc;
    private final LitigesLis litiges;
    private final BinsBanque bins;
    private final ConsultationJournaux journaux;
    private final JournalAudit audit;

    ChargebacksService(JdbcClient jdbc, LitigesLis litiges, BinsBanque bins, ConsultationJournaux journaux,
                       JournalAudit audit) {
        this.jdbc = jdbc;
        this.litiges = litiges;
        this.bins = bins;
        this.journaux = journaux;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ synchronisation avec les LIS

    /**
     * Crée un dossier pour chaque nouveau chargeback des fichiers LIS intégrés et reclasse les dossiers
     * ouverts si les BIN de la banque ont changé. Renvoie le nombre de dossiers créés.
     */
    public int synchroniser() {
        int crees = 0;
        for (OperationLitige o : litiges.chargebacks()) {
            if (o.arn() == null) {
                continue;
            }
            crees += jdbc.sql("""
                            insert into chargeback_dossier (arn, sens, tc, rrn, carte, carte_empreinte, terminal, marchand,
                                date_transaction, heure_transaction, autorisation, montant, devise, motif,
                                reference_chargeback, journee_chargeback)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (arn) do nothing""")
                    .params(o.arn(), sens(o.carte()).name(), o.tc(), o.rrn(), o.carte(), o.carteEmpreinte(), o.terminal(),
                            o.marchand(), o.dateTransaction(), o.heureTransaction(), o.autorisation(), o.montant(),
                            o.devise(), o.motif(), o.referenceChargeback(), o.journee())
                    .update();
        }
        jdbc.sql("select id, carte, sens from chargeback_dossier where not archive").query((rs, n) -> {
            String attendu = sens(rs.getString("carte")).name();
            if (!attendu.equals(rs.getString("sens"))) {
                jdbc.sql("update chargeback_dossier set sens = ? where id = ?").params(attendu, rs.getLong("id")).update();
            }
            return null;
        }).list();
        return crees;
    }

    private Sens sens(String carte) {
        return bins.carteDeLaBanque(carte) ? Sens.EMISSION : Sens.ACQUISITION;
    }

    public boolean binsParametres() {
        return bins.binsParametres();
    }

    // ------------------------------------------------------------------ consultation

    public PageResultat<Dossier> rechercher(Critere c, int page, int taille) {
        synchroniser();
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        long total = jdbc.sql("select count(*) from chargeback_dossier" + where).params(params).query(Long.class).single();
        List<Object> p = new ArrayList<>(params);
        p.add(taille);
        p.add((long) page * taille);
        int delai = delai();
        List<Dossier> l = jdbc.sql("select * from chargeback_dossier" + where
                        + " order by journee_chargeback desc, id desc limit ? offset ?").params(p)
                .query((rs, n) -> dossier(rs)).list().stream().map(d -> avecCycle(d, delai, false)).toList();
        return new PageResultat<>(l, total, page, taille);
    }

    public void parcourir(Critere c, Consumer<Dossier> consommateur) {
        synchroniser();
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        int delai = delai();
        jdbc.sql("select * from chargeback_dossier" + where + " order by journee_chargeback desc, id desc").params(params)
                .query((rs, n) -> dossier(rs)).list().forEach(d -> consommateur.accept(avecCycle(d, delai, false)));
    }

    @Transactional(readOnly = true)
    public Dossier dossier(Sens sens, long id) {
        Dossier d = jdbc.sql("select * from chargeback_dossier where id = ? and sens = ?").params(id, sens.name())
                .query((rs, n) -> dossier(rs)).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Chargeback introuvable."));
        return avecCycle(d, delai(), true);
    }

    private static String where(Critere c, List<Object> params) {
        StringBuilder w = new StringBuilder(" where sens = ?");
        params.add(c.sens().name());
        if (!c.archives()) {
            w.append(" and not archive");
        }
        if (c.reference() != null && !c.reference().isBlank()) {
            // Recherche par ARN ou RRN : la période est alors ignorée, comme dans MONETICA V2.
            w.append(" and (arn = ? or rrn = ?)");
            params.add(c.reference().strip());
            params.add(c.reference().strip());
        } else {
            if (c.du() != null) {
                w.append(" and journee_chargeback >= ?");
                params.add(c.du());
            }
            if (c.au() != null) {
                w.append(" and journee_chargeback <= ?");
                params.add(c.au());
            }
        }
        if (c.regularisation() != null && !c.regularisation().isBlank()) {
            w.append(" and regularisation = ?");
            params.add(c.regularisation());
        }
        return w.toString();
    }

    private int delai() {
        return jdbc.sql("select valeur from parametre_application where cle = 'chargeback.delai_jours'").query(String.class)
                .optional().map(v -> {
                    try {
                        return Integer.parseInt(v.strip());
                    } catch (NumberFormatException e) {
                        return 45;
                    }
                }).orElse(45);
    }

    private static Dossier dossier(ResultSet rs) throws SQLException {
        return new Dossier(rs.getLong("id"), Sens.valueOf(rs.getString("sens")), rs.getString("arn"), rs.getString("tc"),
                LIBELLES_TC.getOrDefault(rs.getString("tc"), "TC " + rs.getString("tc")), rs.getString("rrn"),
                rs.getString("carte"), rs.getString("terminal"), rs.getString("marchand"),
                rs.getObject("date_transaction", LocalDate.class), rs.getString("heure_transaction"),
                rs.getString("autorisation"), (Long) rs.getObject("montant"), rs.getString("devise"), rs.getString("motif"),
                rs.getString("reference_chargeback"), rs.getObject("journee_chargeback", LocalDate.class), null, null, 0, null,
                rs.getString("regularisation"), rs.getString("commentaire"), rs.getString("regularise_par"),
                rs.getObject("regularise_le", OffsetDateTime.class), rs.getBoolean("archive"), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.rapmonet.chargeback.DossiersPorteur.DossierPorteur> dossiers(String debutCarte, String finCarte,
            String empreinte, String reference, LocalDate du, LocalDate au) {
        List<Object> p = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        boolean critere = false;
        if (empreinte != null) {
            w.append(" and carte_empreinte = ?");
            p.add(empreinte);
            critere = true;
        } else if (debutCarte != null && finCarte != null) {
            w.append(" and regexp_replace(coalesce(carte, ''), '\\D', '', 'g') = ?");
            p.add(debutCarte + finCarte);
            critere = true;
        }
        if (reference != null && !reference.isBlank()) {
            w.append(" and (arn = ? or rrn = ? or autorisation = ?)");
            p.add(reference.strip());
            p.add(reference.strip());
            p.add(reference.strip());
            critere = true;
        }
        if (!critere) {
            return List.of();
        }
        if (du != null) {
            w.append(" and coalesce(date_transaction, journee_chargeback) >= ?");
            p.add(du);
        }
        if (au != null) {
            w.append(" and coalesce(date_transaction, journee_chargeback) <= ?");
            p.add(au);
        }
        int delai = delai();
        return jdbc.sql("select * from chargeback_dossier" + w + " order by journee_chargeback desc limit 200").params(p)
                .query((rs, n) -> dossier(rs)).list().stream()
                .map(d -> avecCycle(d, delai, false))
                .map(d -> new com.rapmonet.chargeback.DossiersPorteur.DossierPorteur(d.id(), d.sens().name(), d.arn(),
                        d.libelleTc(), d.rrn(), d.carte(), d.terminal(), d.marchand(), d.dateTransaction(), d.montant(),
                        d.motif(), d.journeeChargeback(), d.etat(), d.situation(), d.regularisation(), d.archive()))
                .toList();
    }

    /** Cycle de vie relu dans les LIS : étapes, situation, état et délai. */
    private Dossier avecCycle(Dossier d, int delai, boolean detail) {
        List<OperationLitige> ops = litiges.cycle(d.arn());
        Cycle c = cycle(d.sens(), d.journeeChargeback(), ops, delai, LocalDate.now());
        List<Operation> operations = detail ? ops.stream().map(o -> new Operation(o.journee(), o.nomFichier(), o.tc(),
                LIBELLES_TC.getOrDefault(o.tc(), "TC " + o.tc()), o.usage(), o.montant(), o.motif(), o.message())).toList() : null;
        return new Dossier(d.id(), d.sens(), d.arn(), d.tc(), d.libelleTc(), d.rrn(), d.carte(), d.terminal(), d.marchand(),
                d.dateTransaction(), d.heureTransaction(), d.autorisation(), d.montant(), d.devise(), d.motif(),
                d.referenceChargeback(), d.journeeChargeback(), c.etat(), c.situation(),
                ChronoUnit.DAYS.between(d.journeeChargeback(), LocalDate.now()), d.journeeChargeback().plusDays(delai),
                d.regularisation(), d.commentaire(), d.regularisePar(), d.regulariseLe(), d.archive(), c.lignes(), operations);
    }

    record Cycle(String etat, String situation, List<LigneCycle> lignes) {
    }

    /** Reconstitue le cycle de vie d'un chargeback à partir des opérations LIS portant son ARN. */
    static Cycle cycle(Sens sens, LocalDate journeeChargeback, List<OperationLitige> ops, int delai, LocalDate aujourdhui) {
        OperationLitige presentation = premier(ops, PRESENTATIONS, "1");
        OperationLitige chargeback = premier(ops, CHARGEBACKS, "1");
        OperationLitige representation = premier(ops, PRESENTATIONS, "2");
        OperationLitige second = premier(ops, CHARGEBACKS, "2");
        OperationLitige annulation = ops.stream().filter(o -> REDRESSEMENTS_CHARGEBACK.contains(o.tc())).findFirst().orElse(null);
        OperationLitige redressement = ops.stream().filter(o -> REDRESSEMENTS_PRESENTATION.contains(o.tc())).findFirst().orElse(null);
        boolean emission = sens == Sens.EMISSION;
        LocalDate echeance = journeeChargeback.plusDays(delai);
        boolean delaiEcoule = aujourdhui.isAfter(echeance);

        List<LigneCycle> l = new ArrayList<>();
        l.add(presentation == null
                ? new LigneCycle("Première présentation", "Transaction non compensée dans les fichiers LIS intégrés", null, true)
                : new LigneCycle("Première présentation", "Compensée le " + JJ.format(presentation.journee())
                + montantTexte(presentation), presentation.journee(), false));
        LocalDate dateCb = chargeback != null ? chargeback.journee() : journeeChargeback;
        String motif = chargeback != null && chargeback.motif() != null ? " – motif " + chargeback.motif() : "";
        l.add(emission
                ? new LigneCycle("Couverture chargeback", "Couverture chargeback reçue le " + JJ.format(dateCb) + motif, dateCb, false)
                : new LigneCycle("Réception chargeback", "Chargeback acquisition reçu le " + JJ.format(dateCb) + motif, dateCb, true));
        if (representation != null) {
            String msg = representation.message() != null ? " (« " + representation.message() + " »)" : "";
            l.add(new LigneCycle("Représentation", (emission ? "Représentation reçue de l'acquéreur le " : "Représentation envoyée le ")
                    + JJ.format(representation.journee()) + msg, representation.journee(), emission));
        } else if (emission) {
            l.add(new LigneCycle("Représentation", delaiEcoule
                    ? "Pas de représentation dans le délai (échéance " + JJ.format(echeance) + ") : chargeback acquis"
                    : "Pas de représentation (échéance " + JJ.format(echeance) + ")", null, false));
        } else {
            l.add(new LigneCycle("Représentation", delaiEcoule
                    ? "Pas de couverture représentation : délai dépassé le " + JJ.format(echeance) + ", chargeback définitif"
                    : "Pas de couverture représentation (à faire avant le " + JJ.format(echeance) + ")", null, true));
        }
        if (second != null) {
            l.add(new LigneCycle("Second chargeback", "Second chargeback le " + JJ.format(second.journee()), second.journee(), true));
        }
        if (redressement != null) {
            l.add(new LigneCycle("Redressement", "Présentation redressée le " + JJ.format(redressement.journee()),
                    redressement.journee(), false));
        }
        if (annulation != null) {
            l.add(new LigneCycle("Annulation du chargeback", "Chargeback redressé (annulé) le " + JJ.format(annulation.journee()),
                    annulation.journee(), false));
        }

        String etat;
        String situation;
        if (annulation != null) {
            etat = "ANNULE";
            situation = "Chargeback annulé";
        } else if (second != null) {
            etat = "SECOND_CHARGEBACK";
            situation = "Second chargeback";
        } else if (representation != null) {
            etat = "REPRESENTE";
            situation = emission ? "Représentation reçue" : "Représenté";
        } else if (delaiEcoule) {
            etat = "DELAI_ECOULE";
            situation = emission ? "Chargeback acquis" : "Délai de représentation dépassé";
        } else {
            etat = "EN_ATTENTE";
            situation = emission ? "En attente de représentation" : "À traiter avant le " + JJ.format(echeance);
        }
        return new Cycle(etat, situation, l);
    }

    private static OperationLitige premier(List<OperationLitige> ops, Set<String> tcs, String usage) {
        return ops.stream().filter(o -> tcs.contains(o.tc()) && usage.equals(o.usage())).findFirst().orElse(null);
    }

    private static String montantTexte(OperationLitige o) {
        return o.montant() == null ? "" : " (" + String.format(java.util.Locale.FRANCE, "%,d", o.montant())
                .replace(' ', ' ').replace(' ', ' ') + " " + Objects.toString(o.devise(), "") + ")";
    }

    // ------------------------------------------------------------------ régularisation et archivage

    public Dossier regulariser(Sens sens, long id, String statut, String commentaire, String utilisateur) {
        Dossier d = dossier(sens, id);
        if (!Set.of("NON", "OUI", "REJET").contains(statut)) {
            throw new RegleMetierException("Statut de régularisation attendu : NON, OUI ou REJET.");
        }
        if (d.archive()) {
            throw new RegleMetierException("Ce chargeback est archivé.");
        }
        if (!statut.equals("NON") && (commentaire == null || commentaire.isBlank())) {
            throw new RegleMetierException("Indiquez un commentaire (écriture passée, motif du rejet…).");
        }
        jdbc.sql("""
                        update chargeback_dossier set regularisation = ?, commentaire = ?, regularise_par = ?, regularise_le = now()
                        where id = ?""")
                .params(statut, commentaire == null || commentaire.isBlank() ? null : commentaire.strip(), utilisateur, id).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.CHARGEBACK_REGULARISATION, "Chargeback ARN " + d.arn(),
                statut + (commentaire == null ? "" : " : " + commentaire), true);
        return dossier(sens, id);
    }

    /** Archive les chargebacks régularisés ou rejetés ; renvoie le nombre de dossiers archivés. */
    public int archiver(Sens sens, String utilisateur) {
        int n = jdbc.sql("""
                        update chargeback_dossier set archive = true, archive_par = ?, archive_le = now()
                        where sens = ? and not archive and regularisation in ('OUI', 'REJET')""")
                .params(utilisateur, sens.name()).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.CHARGEBACK_ARCHIVAGE, "Chargebacks " + sens.name().toLowerCase(),
                n + " dossier(s) archivé(s)", true);
        return n;
    }

    // ------------------------------------------------------------------ journal du GAB (acquisition)

    @Transactional(readOnly = true)
    public JournalGab journalGab(long id) {
        Dossier d = dossier(Sens.ACQUISITION, id);
        if (d.terminal() == null || d.dateTransaction() == null) {
            return new JournalGab("Terminal ou date de transaction absent du chargeback.", false, List.of(), null, null);
        }
        String fin = d.carte() != null && d.carte().length() >= 4 ? d.carte().substring(d.carte().length() - 4) : null;
        // La date LIS peut différer d'un jour de la date du ticket (journée de traitement) : recherche à ±1 jour.
        var critere = new ConsultationJournaux.Critere(d.dateTransaction().minusDays(1), d.dateTransaction().plusDays(1),
                d.terminal(), "RETRAIT", null, null, fin, null, false);
        String recherche = "GAB %s, retraits du %s au %s, carte se terminant par %s, montant %s".formatted(d.terminal(),
                JJ.format(critere.du()), JJ.format(critere.au()), fin, d.montant());
        List<ConsultationJournaux.Operation> ops = journaux.rechercher(critere, 0, 50).elements().stream()
                .filter(o -> d.montant() == null || Objects.equals(o.montant(), d.montant())).toList();
        if (ops.isEmpty()) {
            return new JournalGab("Retrait introuvable dans les journaux intégrés : vérifiez que le journal du GAB "
                    + d.terminal() + " de cette date est chargé.", false, ops, null, recherche);
        }
        ConsultationJournaux.Operation o = ops.getFirst();
        ConsultationJournaux.Session s = journaux.session(o.sessionId()).orElse(null);
        String conclusion = switch (o.statut()) {
            case "DISTRIBUE" -> "Billets présentés et pris par le client : le chargeback peut être contesté (représentation).";
            case "NON_PRIS" -> "Billets présentés mais non pris (retirés par le GAB) : le chargeback est fondé.";
            case "REFUSE" -> "Retrait refusé (réponse " + o.codeReponse() + ") : aucun billet distribué, le chargeback est fondé.";
            default -> "Retrait trouvé sans détail des billets : vérifiez l'extrait du journal.";
        };
        if (ops.size() > 1) {
            conclusion += " (" + ops.size() + " retraits correspondent : vérifiez l'heure.)";
        }
        return new JournalGab(conclusion, "DISTRIBUE".equals(o.statut()), ops, s, recherche);
    }
}
