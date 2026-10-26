package com.rapmonet.reporting.internal;

import com.rapmonet.alertes.SourceAlertes;
import com.rapmonet.chargeback.IndicateursChargebacks;
import com.rapmonet.gab.ReferentielGab;
import com.rapmonet.ingestion.ActiviteJournaux;
import com.rapmonet.ingestion.ActiviteJournaux.JourGab;
import com.rapmonet.ingestion.StatistiquesActivite;
import com.rapmonet.ingestion.StatistiquesActivite.Source;
import com.rapmonet.rapprochement.SyntheseRapprochements;
import com.rapmonet.reclamation.IndicateursReclamations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tableau de bord de la direction : indicateurs clés d'un mois comparés au mois précédent (activité des GAB,
 * monétique, rapprochement, chargebacks, réclamations), tendance sur douze mois, points d'attention et alertes en cours.
 */
@Service
public class TableauBordDirection {

    /** Sens favorable d'une évolution : une hausse des retraits est bonne, une hausse des refus ne l'est pas. */
    public enum Sens { HAUSSE, BAISSE, NEUTRE }

    /**
     * Indicateur comparé au mois précédent.
     *
     * @param unite      NOMBRE, XOF, POURCENT ou JOURS
     * @param variation  évolution en % (ou en points pour un pourcentage), nulle si le mois précédent est à zéro
     */
    public record Indicateur(String libelle, String unite, double valeur, Double precedent, Double variation, Sens favorable,
                             String detail) {
    }

    public record Bloc(String titre, String icone, String lien, List<Indicateur> indicateurs) {
    }

    /** Point d'attention rédigé pour la direction, avec l'écran où agir. */
    public record PointAttention(String niveau, String texte, String lien) {
    }

    public record PointTendance(String mois, long nombre, long montant) {
    }

    public record Tableau(YearMonth mois, YearMonth moisPrecedent, YearMonth dernierMoisAvecDonnees, List<Bloc> blocs,
                         List<PointAttention> pointsAttention, List<PointTendance> tendance, Map<String, Long> alertes) {
    }

    private record Gab(int gabs, int actifs, int couverts, int attendus, long retraits, long montant, long refuses,
                       long captures, long nonPris) {
        double taux() {
            return retraits + refuses == 0 ? 0 : 100.0 * refuses / (retraits + refuses);
        }
    }

    private final ActiviteJournaux journaux;
    private final StatistiquesActivite statistiques;
    private final ReferentielGab referentiel;
    private final SyntheseRapprochements rapprochements;
    private final IndicateursChargebacks chargebacks;
    private final IndicateursReclamations reclamations;
    private final List<SourceAlertes> alertes;

    TableauBordDirection(ActiviteJournaux journaux, StatistiquesActivite statistiques, ReferentielGab referentiel,
                         SyntheseRapprochements rapprochements, IndicateursChargebacks chargebacks,
                         IndicateursReclamations reclamations, List<SourceAlertes> alertes) {
        this.journaux = journaux;
        this.statistiques = statistiques;
        this.referentiel = referentiel;
        this.rapprochements = rapprochements;
        this.chargebacks = chargebacks;
        this.reclamations = reclamations;
        this.alertes = alertes;
    }

    public Tableau tableau(YearMonth mois, LocalDate aujourdhui) {
        YearMonth prec = mois.minusMonths(1);
        LocalDate du = mois.atDay(1), au = mois.atEndOfMonth().isAfter(aujourdhui) ? aujourdhui : mois.atEndOfMonth();
        LocalDate duP = prec.atDay(1), auP = prec.atEndOfMonth();

        Gab g = gab(du, au), gP = gab(duP, auP);
        List<Bloc> blocs = new ArrayList<>();
        blocs.add(new Bloc("Activité des GAB", "local_atm", "/gab/activite", List.of(
                nombre("Retraits distribués", g.retraits(), gP.retraits(), Sens.HAUSSE, null),
                montant("Montant distribué", g.montant(), gP.montant(), Sens.HAUSSE, null),
                pourcent("Taux de refus", g.taux(), gP.taux(), Sens.BAISSE, g.refuses() + " retrait(s) refusé(s)"),
                pourcent("Journaux reçus", g.attendus() == 0 ? 0 : 100.0 * g.couverts() / g.attendus(),
                        gP.attendus() == 0 ? 0 : 100.0 * gP.couverts() / gP.attendus(), Sens.HAUSSE,
                        g.couverts() + " jour(s)-GAB sur " + g.attendus() + " · " + g.gabs() + " GAB paramétré(s)"),
                nombre("Cartes capturées", g.captures(), gP.captures(), Sens.BAISSE, null))));

        long[] confreres = somme(Source.LIS_ACQUISITION_RETRAITS, du, au), confreresP = somme(Source.LIS_ACQUISITION_RETRAITS, duP, auP);
        long[] emisR = somme(Source.LIS_EMISSION_RETRAITS, du, au), emisRP = somme(Source.LIS_EMISSION_RETRAITS, duP, auP);
        long[] emisA = somme(Source.LIS_EMISSION_ACHATS, du, au), emisAP = somme(Source.LIS_EMISSION_ACHATS, duP, auP);
        blocs.add(new Bloc("Monétique (fichiers LIS)", "credit_card", "/reporting/kpi", List.of(
                montant("Retraits de cartes d'autres banques sur nos GAB", confreres[1], confreresP[1], Sens.HAUSSE,
                        confreres[0] + " retrait(s) · commission d'interchange à percevoir"),
                montant("Retraits de nos cartes sur d'autres GAB", emisR[1], emisRP[1], Sens.NEUTRE, emisR[0] + " retrait(s)"),
                montant("Achats de nos cartes chez les commerçants", emisA[1], emisAP[1], Sens.HAUSSE, emisA[0] + " achat(s)"))));

        SyntheseRapprochements.Synthese r = rapprochements.synthese(du, au), rP = rapprochements.synthese(duP, auP);
        blocs.add(new Bloc("Rapprochement GAB ↔ LIS", "compare_arrows", "/rapprochement/gab-lis", List.of(
                pourcent("Retraits confrères rapprochés", r.retraitsConfreres() == 0 ? 0 : 100.0 * r.rapproches() / r.retraitsConfreres(),
                        rP.retraitsConfreres() == 0 ? 0 : 100.0 * rP.rapproches() / rP.retraitsConfreres(), Sens.HAUSSE,
                        r.rapproches() + " sur " + r.retraitsConfreres()),
                montant("Non remontés par le GIM (à réclamer)", r.montantNonRemonte(), rP.montantNonRemonte(), Sens.BAISSE,
                        r.nonRemontes() + " retrait(s)"),
                nombre("Écarts à traiter", r.ecartsATraiter(), rP.ecartsATraiter(), Sens.BAISSE, null))));

        IndicateursChargebacks.Indicateurs c = chargebacks.indicateurs(du, au), cP = chargebacks.indicateurs(duP, auP);
        blocs.add(new Bloc("Chargebacks", "undo", "/chargeback/emission", List.of(
                nombre("Reçus sur nos cartes (émission)", c.recusEmission(), cP.recusEmission(), Sens.BAISSE,
                        String.format("%,d XOF", c.montantRecusEmission()).replace(',', ' ')),
                nombre("Reçus sur nos GAB et TPE (acquisition)", c.recusAcquisition(), cP.recusAcquisition(), Sens.BAISSE,
                        String.format("%,d XOF", c.montantRecusAcquisition()).replace(',', ' ')),
                new Indicateur("Dossiers en cours", "NOMBRE", c.enCours(), null, null, Sens.NEUTRE,
                        String.format("%,d XOF", c.montantEnCours()).replace(',', ' ') + " · à date"),
                new Indicateur("Délai de représentation écoulé", "NOMBRE", c.delaiEcoule(), null, null, Sens.BAISSE, "à date"))));

        IndicateursReclamations.Indicateurs q = reclamations.indicateurs(du, au), qP = reclamations.indicateurs(duP, auP);
        blocs.add(new Bloc("Réclamations porteurs", "support_agent", "/reclamations/registre", List.of(
                nombre("Réclamations reçues", q.recues(), qP.recues(), Sens.BAISSE,
                        String.format("%,d XOF contestés", q.montantConteste()).replace(',', ' ')),
                nombre("Réclamations closes", q.closes(), qP.closes(), Sens.HAUSSE,
                        q.closes() == 0 ? null : Math.round(100.0 * q.fondees() / q.closes()) + " % fondées"),
                new Indicateur("Délai moyen de traitement", "JOURS", q.delaiMoyenJours(), qP.delaiMoyenJours(),
                        qP.delaiMoyenJours() == 0 ? null : Math.round(10.0 * (q.delaiMoyenJours() - qP.delaiMoyenJours())) / 10.0,
                        Sens.BAISSE, null),
                new Indicateur("En retard", "NOMBRE", q.enRetard(), null, null, Sens.BAISSE, q.ouvertes() + " ouverte(s) · à date"))));

        List<PointAttention> points = new ArrayList<>();
        if (r.nonRemontes() > 0) {
            points.add(new PointAttention("ATTENTION", r.nonRemontes() + " retrait(s) de cartes d'autres banques non remonté(s) par le GIM : "
                    + xof(r.montantNonRemonte()) + " à réclamer.", "/rapprochement/gab-lis"));
        }
        if (c.delaiEcoule() > 0) {
            points.add(new PointAttention("ATTENTION", c.delaiEcoule() + " chargeback(s) dont le délai de représentation est écoulé.",
                    "/chargeback/acquisition"));
        }
        if (q.enRetard() > 0) {
            points.add(new PointAttention("ATTENTION", q.enRetard() + " réclamation(s) porteur au-delà de l'échéance de réponse.",
                    "/reclamations/registre"));
        }
        if (g.retraits() + g.refuses() >= 50 && g.taux() - gP.taux() >= 2) {
            points.add(new PointAttention("ATTENTION", String.format(java.util.Locale.FRANCE, "Taux de refus des retraits en hausse : %.1f %% contre %.1f %% le mois précédent.",
                    g.taux(), gP.taux()), "/gab/activite"));
        }
        if (g.attendus() > g.couverts()) {
            points.add(new PointAttention("INFO", (g.attendus() - g.couverts()) + " jour(s)-GAB sans journal intégré sur le mois.",
                    "/ingestion/ejournal"));
        }
        if (g.captures() > 0) {
            points.add(new PointAttention("INFO", g.captures() + " carte(s) capturée(s) par les GAB.", "/gab/activite"));
        }
        if (gP.montant() > 0 && g.montant() < 0.8 * gP.montant() && !au.isBefore(mois.atEndOfMonth())) {
            points.add(new PointAttention("INFO", String.format(java.util.Locale.FRANCE, "Montant distribué par les GAB en baisse de %.0f %%.",
                    100.0 * (gP.montant() - g.montant()) / gP.montant()), "/gab/activite"));
        }

        List<PointTendance> tendance = new ArrayList<>();
        YearMonth debut = mois.minusMonths(11);
        Map<YearMonth, long[]> parMois = statistiques.agreger(Source.JOURNAUX_RETRAITS, debut.atDay(1), mois.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(StatistiquesActivite.Agregat::mois,
                        Collectors.reducing(new long[2], a -> new long[] { a.nombre(), a.montant() },
                                (x, y) -> new long[] { x[0] + y[0], x[1] + y[1] })));
        for (YearMonth m = debut; !m.isAfter(mois); m = m.plusMonths(1)) {
            long[] v = parMois.getOrDefault(m, new long[2]);
            tendance.add(new PointTendance(m.toString(), v[0], v[1]));
        }

        Map<String, Long> niveaux = alertes.stream().flatMap(s -> s.alertes(aujourdhui).stream()).filter(a -> a.nombre() > 0)
                .collect(Collectors.groupingBy(a -> a.niveau().name(), Collectors.counting()));
        YearMonth dernier = journaux.derniersJournaux().values().stream().max(Comparator.naturalOrder())
                .map(YearMonth::from).orElse(null);
        return new Tableau(mois, prec, dernier, blocs, points, tendance, niveaux);
    }

    private Gab gab(LocalDate du, LocalDate au) {
        Set<String> parametres = referentiel.libellesGab().keySet();
        List<JourGab> jours = journaux.jours(du, au, null, LocalTime.MIDNIGHT, LocalTime.of(23, 59));
        int nbJours = (int) (au.toEpochDay() - du.toEpochDay()) + 1;
        int couverts = (int) jours.stream().filter(j -> parametres.contains(j.terminal())).count();
        return new Gab(parametres.size(), (int) jours.stream().map(JourGab::terminal).distinct().count(), couverts,
                parametres.size() * nbJours, jours.stream().mapToLong(JourGab::retraitsDistribues).sum(),
                jours.stream().mapToLong(JourGab::montantDistribue).sum(), jours.stream().mapToLong(JourGab::retraitsRefuses).sum(),
                jours.stream().mapToLong(JourGab::cartesCapturees).sum(), jours.stream().mapToLong(JourGab::billetsNonPris).sum());
    }

    private long[] somme(Source source, LocalDate du, LocalDate au) {
        long n = 0, m = 0;
        for (StatistiquesActivite.Agregat a : statistiques.agreger(source, du, au)) {
            n += a.nombre();
            m += a.montant();
        }
        return new long[] { n, m };
    }

    private static Indicateur nombre(String libelle, long v, long p, Sens sens, String detail) {
        return new Indicateur(libelle, "NOMBRE", v, (double) p, p == 0 ? null : Math.round(1000.0 * (v - p) / p) / 10.0, sens, detail);
    }

    private static Indicateur montant(String libelle, long v, long p, Sens sens, String detail) {
        return new Indicateur(libelle, "XOF", v, (double) p, p == 0 ? null : Math.round(1000.0 * (v - p) / p) / 10.0, sens, detail);
    }

    /** Pourcentage : la variation est exprimée en points. */
    private static Indicateur pourcent(String libelle, double v, double p, Sens sens, String detail) {
        double vv = Math.round(v * 10) / 10.0, pp = Math.round(p * 10) / 10.0;
        return new Indicateur(libelle, "POURCENT", vv, pp, Math.round((vv - pp) * 10) / 10.0, sens, detail);
    }

    private static String xof(long montant) {
        return String.format("%,d XOF", montant).replace(',', ' ');
    }
}
