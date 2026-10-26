package com.rapmonet.rapprochement.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.gab.BinsBanque;
import com.rapmonet.ingestion.ConsultationJournaux;
import com.rapmonet.ingestion.RetraitsAcquis;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rapprochement des retraits de cartes d'autres banques sur les GAB de la banque : chaque retrait distribué du
 * journal NCR doit être remonté par le GIM dans un avis d'acquisition LIS (TC 49, code commerçant 6011), et
 * chaque avis doit correspondre à un retrait du journal.
 *
 * <p>Les retraits des cartes de la banque (compte imprimé sur le ticket, ou BIN de la banque) ne passent pas par
 * le GIM : ils sont comptés à part et seront rapprochés avec Amplitude.
 *
 * <p>Appariement, selon les règles paramétrées (ReglesGabLis) : même terminal, même carte (6 premiers et 4 derniers
 * chiffres) si exigé, montant à la tolérance près, heure du ticket et heure de l'avis à moins de la tolérance ; à
 * défaut, si la référence est utilisée, référence du ticket égale à l'un des champs choisis de l'avis (code
 * d'autorisation, numéro de bordereau, fin du RRN) dans la fenêtre de jours.
 */
@Service
@Transactional(readOnly = true)
public class RapprochementGabLis {

    public enum Resultat {
        RAPPROCHE("Rapproché"),
        RETRAIT_NON_REMONTE("Retrait non remonté par le GIM"),
        AVIS_SANS_RETRAIT("Avis LIS sans retrait au journal"),
        EN_ATTENTE_LIS("En attente des LIS"),
        JOURNAL_ABSENT("Journal du GAB absent");

        public final String libelle;

        Resultat(String libelle) {
            this.libelle = libelle;
        }

        boolean ecart() {
            return this == RETRAIT_NON_REMONTE || this == AVIS_SANS_RETRAIT;
        }
    }

    public enum Vue { ECARTS, RAPPROCHES, TOUS }

    /**
     * Une ligne du rapprochement : retrait du journal, avis LIS, ou les deux.
     *
     * @param traitement A_TRAITER, JUSTIFIE ou REGULARISE (écarts seulement)
     */
    public record Ligne(String cle, Resultat resultat, String libelleResultat, String terminal, LocalDate date,
                        LocalDateTime dateHeureJournal, String referenceJournal, LocalDateTime dateHeureLis,
                        String autorisation, String rrn, String reseau, LocalDate journeeLis, String fichierLis,
                        String carte, long montant, Long montantLis, Long ecartMinutes, String traitement, String commentaire,
                        String traitePar, OffsetDateTime traiteLe) {
    }

    public record Synthese(int retraitsConfreres, long montantRetraitsConfreres, int retraitsOnUs, long montantOnUs,
                           int avisLis, long montantAvisLis, int rapproches, long montantRapproche,
                           int nonRemontes, long montantNonRemonte, int avisSansRetrait, long montantAvisSansRetrait,
                           int enAttente, long montantEnAttente, int journalAbsent, long montantJournalAbsent,
                           int ecartsATraiter, LocalDate derniereJourneeLis, int toleranceMinutes, int delaiLisJours,
                           ReglesGabLis.Regles regles) {
    }

    public record Rapprochement(Synthese synthese, List<Ligne> lignes) {
    }

    private record Traitement(String statut, String commentaire, String traitePar, OffsetDateTime traiteLe) {
    }

    private static final Set<String> STATUTS = Set.of("A_TRAITER", "JUSTIFIE", "REGULARISE");

    /**
     * Opération dont le résultat change entre les règles en vigueur et les règles proposées.
     *
     * @param avant résultat avec les règles en vigueur, null si l'opération n'y apparaît pas seule
     */
    public record Changement(String terminal, LocalDate date, String carte, long montant, String avant, String apres) {
    }

    /** Effet de règles proposées sur une période, avant de les enregistrer. */
    public record Simulation(Synthese actuelle, Synthese proposee, List<Changement> changements, int nbChangements) {
    }

    private final ReglesGabLis reglesGabLis;
    private final ConsultationJournaux journaux;
    private final RetraitsAcquis lis;
    private final BinsBanque bins;
    private final JdbcClient jdbc;
    private final JournalAudit audit;

    RapprochementGabLis(ReglesGabLis reglesGabLis, ConsultationJournaux journaux, RetraitsAcquis lis, BinsBanque bins,
                        JdbcClient jdbc, JournalAudit audit) {
        this.reglesGabLis = reglesGabLis;
        this.journaux = journaux;
        this.lis = lis;
        this.bins = bins;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public Rapprochement rapprocher(LocalDate du, LocalDate au, String terminal, Vue vue) {
        return rapprocher(du, au, terminal, vue, reglesGabLis.lire());
    }

    /** Compare, sur une période, les résultats des règles en vigueur et ceux des règles proposées. */
    public Simulation simuler(LocalDate du, LocalDate au, String terminal, ReglesGabLis.Regles proposees) {
        ReglesGabLis.Regles r = reglesGabLis.valider(proposees);
        Rapprochement actuel = rapprocher(du, au, terminal, Vue.TOUS, reglesGabLis.lire());
        Rapprochement propose = rapprocher(du, au, terminal, Vue.TOUS, r);
        Map<String, String> avant = new HashMap<>();
        actuel.lignes().forEach(l -> avant.put(l.cle(), l.libelleResultat()));
        List<Changement> changements = new ArrayList<>();
        for (Ligne l : propose.lignes()) {
            String a = avant.get(l.cle());
            if (!l.libelleResultat().equals(a)) {
                changements.add(new Changement(l.terminal(), l.date(), l.carte(), l.montant(), a, l.libelleResultat()));
            }
        }
        return new Simulation(actuel.synthese(), propose.synthese(),
                changements.stream().limit(500).toList(), changements.size());
    }

    Rapprochement rapprocher(LocalDate du, LocalDate au, String terminal, Vue vue, ReglesGabLis.Regles regles) {
        if (du == null || au == null || du.isAfter(au)) {
            throw new RegleMetierException("Indiquez une période valide (du … au …).");
        }
        if (Duration.between(du.atStartOfDay(), au.atStartOfDay()).toDays() > 366) {
            throw new RegleMetierException("La période est limitée à un an.");
        }
        String gab = terminal == null || terminal.isBlank() ? null : terminal.strip();
        int tolerance = regles.toleranceMinutes();
        int delai = regles.delaiLisJours();
        Optional<LocalDate> derniereJournee = lis.derniereJournee();

        // Journal : retraits distribués, séparés entre cartes de la banque et cartes confrères.
        List<ConsultationJournaux.Operation> confreres = new ArrayList<>();
        int onUs = 0;
        long montantOnUs = 0;
        List<ConsultationJournaux.Operation> retraits = new ArrayList<>();
        journaux.parcourir(new ConsultationJournaux.Critere(du, au, gab, "RETRAIT", "DISTRIBUE", null, null, null, false),
                retraits::add);
        for (ConsultationJournaux.Operation o : retraits) {
            if (o.montant() == null || o.dateHeure() == null) {
                continue;
            }
            if ((o.compte() != null && !o.compte().isBlank()) || bins.carteDeLaBanque(o.carte())) {
                onUs++;
                montantOnUs += o.montant();
            } else {
                confreres.add(o);
            }
        }

        // LIS : avis de la période, élargie d'un jour pour les retraits proches de minuit.
        List<RetraitsAcquis.Avis> avis = lis.avis(du.minusDays(1), au.plusDays(1), gab);
        Map<String, List<RetraitsAcquis.Avis>> avisParCle = new HashMap<>();
        for (RetraitsAcquis.Avis a : avis) {
            avisParCle.computeIfAbsent(cle(a.terminal(), a.carte(), regles.memeCarte()), k -> new ArrayList<>()).add(a);
        }

        Map<String, Traitement> traitements = traitements();
        List<Ligne> lignes = new ArrayList<>();
        Set<Long> apparies = new java.util.HashSet<>();
        confreres.sort(Comparator.comparing(ConsultationJournaux.Operation::dateHeure));
        for (ConsultationJournaux.Operation o : confreres) {
            RetraitsAcquis.Avis choisi = null;
            long meilleur = Long.MAX_VALUE;
            for (RetraitsAcquis.Avis a : avisParCle.getOrDefault(cle(o.terminal(), o.carte(), regles.memeCarte()), List.of())) {
                long ecartMontant = Math.abs(a.montant() - o.montant());
                if (apparies.contains(a.id()) || ecartMontant > regles.toleranceMontant()) {
                    continue;
                }
                long ecart = ecartMinutes(o.dateHeure(), a);
                boolean memeReference = regles.reference() && memeReference(o.reference(), a, regles.champsReference());
                boolean accepte = ecart <= tolerance
                        || (memeReference && Math.abs(Duration.between(o.dateHeure().toLocalDate().atStartOfDay(),
                        a.dateTransaction().atStartOfDay()).toDays()) <= regles.fenetreReferenceJours());
                // Priorité : référence identique, puis montant exact, puis écart horaire le plus faible.
                long score = (memeReference ? 0 : 1_000_000_000L) + ecartMontant * 10_000 + ecart;
                if (accepte && score < meilleur) {
                    meilleur = score;
                    choisi = a;
                }
            }
            String cle = "EJ:" + o.terminal() + "|" + o.dateHeure() + "|" + o.carte() + "|" + o.reference() + "|" + o.montant();
            if (choisi != null) {
                apparies.add(choisi.id());
                lignes.add(ligne(cle, Resultat.RAPPROCHE, o, choisi, ecartMinutes(o.dateHeure(), choisi), null));
            } else {
                LocalDate date = o.dateHeure().toLocalDate();
                boolean attente = derniereJournee.isEmpty() || derniereJournee.get().isBefore(date.plusDays(delai));
                Resultat r = attente ? Resultat.EN_ATTENTE_LIS : Resultat.RETRAIT_NON_REMONTE;
                lignes.add(ligne(cle, r, o, null, null, r.ecart() ? traitements.get(cle) : null));
            }
        }
        Map<String, Boolean> couverture = new HashMap<>();
        for (RetraitsAcquis.Avis a : avis) {
            if (apparies.contains(a.id()) || a.dateTransaction().isBefore(du) || a.dateTransaction().isAfter(au)) {
                continue;
            }
            boolean journal = couverture.computeIfAbsent(a.terminal() + "|" + a.dateTransaction(),
                    k -> a.terminal() != null && journaux.joursCouverts(a.terminal(), a.dateTransaction(), a.dateTransaction()) > 0);
            Resultat r = journal ? Resultat.AVIS_SANS_RETRAIT : Resultat.JOURNAL_ABSENT;
            String cle = "LIS:" + a.cle();
            lignes.add(ligne(cle, r, null, a, null, r.ecart() ? traitements.get(cle) : null));
        }

        Synthese s = synthese(lignes, onUs, montantOnUs, derniereJournee.orElse(null), regles);
        List<Ligne> affichees = lignes.stream()
                .filter(l -> switch (vue == null ? Vue.ECARTS : vue) {
                    case ECARTS -> l.resultat() != Resultat.RAPPROCHE;
                    case RAPPROCHES -> l.resultat() == Resultat.RAPPROCHE;
                    case TOUS -> true;
                })
                .sorted(Comparator.comparing((Ligne l) -> l.resultat().ordinal() == 0 ? 9 : l.resultat().ordinal())
                        .thenComparing(Ligne::date, Comparator.reverseOrder())
                        .thenComparing(Ligne::terminal, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new Rapprochement(s, affichees);
    }

    /** Traitement d'un écart : JUSTIFIE ou REGULARISE avec un commentaire, ou A_TRAITER pour annuler le traitement. */
    @Transactional
    public void traiter(String cle, String typeEcart, String statut, String commentaire, String utilisateur) {
        if (cle == null || !(cle.startsWith("EJ:") || cle.startsWith("LIS:")) || cle.length() > 320) {
            throw new RegleMetierException("Écart inconnu.");
        }
        if (statut == null || !STATUTS.contains(statut)) {
            throw new RegleMetierException("Statut attendu : A_TRAITER, JUSTIFIE ou REGULARISE.");
        }
        String texte = commentaire == null ? "" : commentaire.strip();
        if (!"A_TRAITER".equals(statut) && texte.isEmpty()) {
            throw new RegleMetierException("Le commentaire est obligatoire pour justifier ou régulariser un écart.");
        }
        if (texte.length() > 500) {
            throw new RegleMetierException("Le commentaire est limité à 500 caractères.");
        }
        if ("A_TRAITER".equals(statut)) {
            jdbc.sql("delete from rapprochement_ecart where cle = ?").param(cle).update();
        } else {
            jdbc.sql("""
                            insert into rapprochement_ecart (cle, type_ecart, statut, commentaire, traite_par) values (?, ?, ?, ?, ?)
                            on conflict (cle) do update set statut = excluded.statut, commentaire = excluded.commentaire,
                                traite_par = excluded.traite_par, traite_le = now()""")
                    .params(cle, typeEcart == null ? "" : typeEcart, statut, texte, utilisateur).update();
        }
        String objet = "Écart GAB / LIS " + cle;
        audit.enregistrerPour(utilisateur, ActionsAudit.RAPPROCHEMENT_ECART, objet.length() > 200 ? objet.substring(0, 200) : objet,
                statut + (texte.isEmpty() ? "" : " : " + texte), true);
    }

    // ------------------------------------------------------------------ outils

    private Ligne ligne(String cle, Resultat r, ConsultationJournaux.Operation o, RetraitsAcquis.Avis a, Long ecart,
                        Traitement t) {
        LocalDateTime lisDateHeure = a == null ? null : a.dateTransaction().atTime(a.heure() == null ? LocalTime.MIDNIGHT : a.heure());
        return new Ligne(cle, r, r.libelle, o != null ? o.terminal() : a.terminal(),
                o != null ? o.dateHeure().toLocalDate() : a.dateTransaction(),
                o == null ? null : o.dateHeure(), o == null ? null : o.reference(), lisDateHeure,
                a == null ? null : a.autorisation(), a == null ? null : a.rrn(), a == null ? null : a.reseau(),
                a == null ? null : a.journee(), a == null ? null : a.fichier(),
                o != null ? o.carte() : a.carte(), o != null ? o.montant() : a.montant(), a == null ? null : a.montant(), ecart,
                r.ecart() ? (t == null ? "A_TRAITER" : t.statut()) : null,
                t == null ? null : t.commentaire(), t == null ? null : t.traitePar(), t == null ? null : t.traiteLe());
    }

    private static Synthese synthese(List<Ligne> lignes, int onUs, long montantOnUs, LocalDate derniere,
                                     ReglesGabLis.Regles regles) {
        Map<Resultat, long[]> c = new LinkedHashMap<>();
        for (Resultat r : Resultat.values()) {
            c.put(r, new long[2]);
        }
        int confreres = 0;
        long montantConfreres = 0;
        int avis = 0;
        long montantAvis = 0;
        int aTraiter = 0;
        for (Ligne l : lignes) {
            c.get(l.resultat())[0]++;
            c.get(l.resultat())[1] += l.montant();
            if (l.dateHeureJournal() != null) {
                confreres++;
                montantConfreres += l.montant();
            }
            if (l.fichierLis() != null) {
                avis++;
                montantAvis += l.montantLis();
            }
            if ("A_TRAITER".equals(l.traitement())) {
                aTraiter++;
            }
        }
        return new Synthese(confreres, montantConfreres, onUs, montantOnUs, avis, montantAvis,
                (int) c.get(Resultat.RAPPROCHE)[0], c.get(Resultat.RAPPROCHE)[1],
                (int) c.get(Resultat.RETRAIT_NON_REMONTE)[0], c.get(Resultat.RETRAIT_NON_REMONTE)[1],
                (int) c.get(Resultat.AVIS_SANS_RETRAIT)[0], c.get(Resultat.AVIS_SANS_RETRAIT)[1],
                (int) c.get(Resultat.EN_ATTENTE_LIS)[0], c.get(Resultat.EN_ATTENTE_LIS)[1],
                (int) c.get(Resultat.JOURNAL_ABSENT)[0], c.get(Resultat.JOURNAL_ABSENT)[1],
                aTraiter, derniere, regles.toleranceMinutes(), regles.delaiLisJours(), regles);
    }

    private Map<String, Traitement> traitements() {
        Map<String, Traitement> m = new HashMap<>();
        jdbc.sql("select cle, statut, commentaire, traite_par, traite_le from rapprochement_ecart")
                .query((rs, n) -> m.put(rs.getString(1), new Traitement(rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, OffsetDateTime.class))))
                .list();
        return m;
    }

    /** Terminal, et si la carte est comparée, ses 6 premiers et 4 derniers chiffres. */
    static String cle(String terminal, String carte, boolean memeCarte) {
        String t = terminal == null ? "" : terminal.strip();
        if (!memeCarte) {
            return t;
        }
        String chiffres = carte == null ? "" : carte.replaceAll("\\D", "");
        String c = chiffres.length() >= 10 ? chiffres.substring(0, 6) + chiffres.substring(chiffres.length() - 4) : chiffres;
        return t + "|" + c;
    }

    /** Minutes entre le ticket et l'avis ; sans heure LIS, 0 le même jour et sinon l'écart en jours. */
    static long ecartMinutes(LocalDateTime ticket, RetraitsAcquis.Avis a) {
        if (a.heure() == null) {
            return Math.abs(Duration.between(ticket.toLocalDate().atStartOfDay(), a.dateTransaction().atStartOfDay()).toMinutes());
        }
        return Math.abs(Duration.between(ticket, a.dateTransaction().atTime(a.heure())).toMinutes());
    }

    static boolean memeReference(String reference, RetraitsAcquis.Avis a, Set<ReglesGabLis.ChampReference> champs) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        String r = reference.strip();
        return (champs.contains(ReglesGabLis.ChampReference.AUTORISATION) && r.equals(a.autorisation()))
                || (champs.contains(ReglesGabLis.ChampReference.BORDEREAU) && r.equals(a.bordereau()))
                || (champs.contains(ReglesGabLis.ChampReference.RRN) && a.rrn() != null && a.rrn().endsWith(r));
    }
}
