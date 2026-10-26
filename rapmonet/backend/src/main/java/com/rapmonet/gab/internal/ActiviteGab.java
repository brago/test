package com.rapmonet.gab.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.ActiviteJournaux;
import com.rapmonet.ingestion.ActiviteJournaux.JourGab;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tableau de bord de l'activité des GAB : retraits, refus, incidents et inactivité par GAB et par jour, à partir des
 * journaux NCR intégrés. Un GAB est signalé quand son taux de refus, son inactivité ou ses cartes capturées dépassent
 * les seuils paramétrés (gab.activite.*), ou quand des jours de la période n'ont pas de journal.
 */
@Service
@Transactional(readOnly = true)
public class ActiviteGab {

    /** Seuils et plage horaire de surveillance. */
    public record Seuils(LocalTime debutPlage, LocalTime finPlage, int inactiviteMinutes, int tauxRefus, int tentativesMin) {
    }

    public record Signalement(String code, String libelle) {
    }

    /**
     * Activité d'un GAB sur la période.
     *
     * @param tauxRefus  retraits refusés sur retraits tentés, en %
     * @param parametre  GAB déclaré dans le paramétrage (sinon vu seulement dans les journaux)
     */
    public record LigneGab(String terminal, String libelle, String localisation, String agence, boolean parametre,
                           boolean actif, int joursCouverts, int joursPeriode, int sessions, int retraits, long montant,
                           long montantMoyenJour, int refuses, double tauxRefus, String motifPrincipal, int nonPris,
                           int cartesCapturees, int annulations, int incidentsEmv, int plusLongueInactivite,
                           LocalDate dateInactivite, LocalTime debutInactivite, LocalDate dernierJournal,
                           List<Signalement> signalements) {
    }

    public record Point(String libelle, long nombre, long montant) {
    }

    public record Motif(String code, String libelle, int nombre) {
    }

    public record Synthese(int gabs, int gabsAvecActivite, int gabsSignales, int retraits, long montant, int refuses,
                           double tauxRefus, int nonPris, int cartesCapturees) {
    }

    public record Activite(LocalDate du, LocalDate au, Seuils seuils, Synthese synthese, List<LigneGab> gabs,
                           List<Point> parJour, List<Point> parHeure, List<Motif> refus) {
    }

    /** Détail jour par jour d'un GAB. */
    public record JourDetaille(JourGab jour, double tauxRefus, List<Signalement> signalements) {
    }

    public record DetailGab(LigneGab gab, List<JourDetaille> jours, List<LocalDate> joursSansJournal,
                            List<Point> parHeure, List<Motif> refus) {
    }

    private record Gab(String terminalId, String libelle, String localisation, String agence, boolean actif) {
    }

    private final ActiviteJournaux journaux;
    private final JdbcClient jdbc;

    ActiviteGab(ActiviteJournaux journaux, JdbcClient jdbc) {
        this.journaux = journaux;
        this.jdbc = jdbc;
    }

    public Seuils seuils() {
        Map<String, String> v = new HashMap<>();
        jdbc.sql("select cle, valeur from parametre_application where cle like 'gab.activite.%'")
                .query(rs -> {
                    v.put(rs.getString(1).substring("gab.activite.".length()), rs.getString(2).strip());
                });
        return new Seuils(heure(v.get("plage_debut"), LocalTime.of(7, 0)), heure(v.get("plage_fin"), LocalTime.of(21, 0)),
                entier(v.get("inactivite_alerte_minutes"), 180), entier(v.get("taux_refus_alerte"), 25),
                entier(v.get("tentatives_min"), 10));
    }

    public Activite activite(LocalDate du, LocalDate au, String terminal) {
        controlerPeriode(du, au);
        Seuils s = seuils();
        List<JourGab> jours = journaux.jours(du, au, terminal, s.debutPlage(), s.finPlage());
        Map<String, String> codes = journaux.libellesCodesReponse();
        Map<String, LocalDate> derniers = journaux.derniersJournaux();
        Map<String, Gab> gabs = gabs();

        Map<String, List<JourGab>> parGab = new TreeMap<>();
        jours.forEach(j -> parGab.computeIfAbsent(j.terminal(), k -> new ArrayList<>()).add(j));
        TreeSet<String> terminaux = new TreeSet<>(parGab.keySet());
        gabs.values().stream().filter(Gab::actif).map(Gab::terminalId).forEach(terminaux::add);
        if (terminal != null && !terminal.isBlank()) {
            terminaux.removeIf(t -> !t.equals(terminal.strip()));
        }

        List<LigneGab> lignes = new ArrayList<>();
        for (String t : terminaux) {
            lignes.add(ligne(t, gabs.get(t), parGab.getOrDefault(t, List.of()), du, au, s, codes, derniers.get(t)));
        }
        lignes.sort(Comparator.comparing((LigneGab l) -> l.signalements().isEmpty()).thenComparing(LigneGab::terminal));

        Map<LocalDate, long[]> jour = new TreeMap<>();
        long[] heures = new long[24];
        Map<String, Integer> refus = new HashMap<>();
        for (JourGab j : jours) {
            long[] c = jour.computeIfAbsent(j.date(), k -> new long[2]);
            c[0] += j.retraitsDistribues();
            c[1] += j.montantDistribue();
            for (int h = 0; h < 24; h++) {
                heures[h] += j.retraitsParHeure().get(h);
            }
            j.refusParCode().forEach((code, n) -> refus.merge(code, n, Integer::sum));
        }
        List<Point> parJour = new ArrayList<>();
        for (LocalDate d = du; !d.isAfter(au); d = d.plusDays(1)) {
            long[] c = jour.getOrDefault(d, new long[2]);
            parJour.add(new Point(d.toString(), c[0], c[1]));
        }
        int retraits = lignes.stream().mapToInt(LigneGab::retraits).sum();
        int refuses = lignes.stream().mapToInt(LigneGab::refuses).sum();
        Synthese synthese = new Synthese(lignes.size(), (int) lignes.stream().filter(l -> l.sessions() > 0).count(),
                (int) lignes.stream().filter(l -> !l.signalements().isEmpty()).count(), retraits,
                lignes.stream().mapToLong(LigneGab::montant).sum(), refuses, taux(refuses, retraits + refuses),
                lignes.stream().mapToInt(LigneGab::nonPris).sum(), lignes.stream().mapToInt(LigneGab::cartesCapturees).sum());
        return new Activite(du, au, s, synthese, lignes, parJour, parHeure(heures), motifs(refus, codes));
    }

    public DetailGab detail(String terminal, LocalDate du, LocalDate au) {
        controlerPeriode(du, au);
        if (terminal == null || terminal.isBlank()) {
            throw new RegleMetierException("Indiquez le GAB.");
        }
        Seuils s = seuils();
        List<JourGab> jours = journaux.jours(du, au, terminal.strip(), s.debutPlage(), s.finPlage());
        Map<String, String> codes = journaux.libellesCodesReponse();
        LigneGab ligne = ligne(terminal.strip(), gabs().get(terminal.strip()), jours, du, au, s, codes,
                journaux.derniersJournaux().get(terminal.strip()));
        List<JourDetaille> detail = jours.stream().sorted(Comparator.comparing(JourGab::date).reversed())
                .map(j -> new JourDetaille(j, taux(j.retraitsRefuses(), j.retraitsDistribues() + j.retraitsRefuses()),
                        signalementsJour(j, s))).toList();
        List<LocalDate> sansJournal = new ArrayList<>();
        for (LocalDate d = du; !d.isAfter(au); d = d.plusDays(1)) {
            LocalDate x = d;
            if (jours.stream().noneMatch(j -> j.date().equals(x))) {
                sansJournal.add(d);
            }
        }
        long[] heures = new long[24];
        Map<String, Integer> refus = new HashMap<>();
        jours.forEach(j -> {
            for (int h = 0; h < 24; h++) {
                heures[h] += j.retraitsParHeure().get(h);
            }
            j.refusParCode().forEach((code, n) -> refus.merge(code, n, Integer::sum));
        });
        return new DetailGab(ligne, detail, sansJournal, parHeure(heures), motifs(refus, codes));
    }

    /** Signalements de la veille, pour les alertes de l'accueil : GAB et motif. */
    public Map<String, List<Signalement>> signalementsDuJour(LocalDate jour) {
        Seuils s = seuils();
        Map<String, List<Signalement>> m = new TreeMap<>();
        for (JourGab j : journaux.jours(jour, jour, null, s.debutPlage(), s.finPlage())) {
            List<Signalement> l = signalementsJour(j, s);
            if (!l.isEmpty()) {
                m.put(j.terminal(), l);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ calculs

    private LigneGab ligne(String t, Gab g, List<JourGab> jours, LocalDate du, LocalDate au, Seuils s,
                           Map<String, String> codes, LocalDate dernier) {
        int joursPeriode = (int) ChronoUnit.DAYS.between(du, au) + 1;
        int sessions = 0, retraits = 0, refuses = 0, nonPris = 0, captures = 0, annulations = 0, emv = 0;
        long montant = 0;
        JourGab pire = null;
        Map<String, Integer> refus = new HashMap<>();
        List<Signalement> signalements = new ArrayList<>();
        Map<String, Signalement> uniques = new LinkedHashMap<>();
        for (JourGab j : jours) {
            sessions += j.sessions();
            retraits += j.retraitsDistribues();
            montant += j.montantDistribue();
            refuses += j.retraitsRefuses();
            nonPris += j.billetsNonPris();
            captures += j.cartesCapturees();
            annulations += j.annulations();
            emv += j.incidentsEmv();
            j.refusParCode().forEach((code, n) -> refus.merge(code, n, Integer::sum));
            if (pire == null || j.plusLongueInactivite() > pire.plusLongueInactivite()) {
                pire = j;
            }
            signalementsJour(j, s).forEach(x -> uniques.putIfAbsent(x.code(), x));
        }
        signalements.addAll(uniques.values());
        boolean actif = g != null && g.actif();
        int couverts = jours.size();
        if (actif && couverts < joursPeriode) {
            signalements.add(new Signalement("JOURS_SANS_JOURNAL", (joursPeriode - couverts) + " jour(s) sans journal"));
        }
        String motif = refus.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " " + codes.getOrDefault(e.getKey(), "") + " (" + e.getValue() + ")").orElse(null);
        return new LigneGab(t, g == null ? null : g.libelle(), g == null ? null : g.localisation(),
                g == null ? null : g.agence(), g != null, actif, couverts, joursPeriode, sessions, retraits, montant,
                couverts == 0 ? 0 : montant / couverts, refuses, taux(refuses, retraits + refuses), motif, nonPris,
                captures, annulations, emv, pire == null ? 0 : pire.plusLongueInactivite(),
                pire == null ? null : pire.date(), pire == null ? null : pire.debutInactivite(), dernier,
                List.copyOf(signalements));
    }

    private static List<Signalement> signalementsJour(JourGab j, Seuils s) {
        List<Signalement> l = new ArrayList<>();
        int tentatives = j.retraitsDistribues() + j.retraitsRefuses();
        double taux = taux(j.retraitsRefuses(), tentatives);
        if (tentatives >= s.tentativesMin() && taux >= s.tauxRefus()) {
            l.add(new Signalement("TAUX_REFUS", "Refus élevés (" + Math.round(taux) + " % le " + jj(j.date()) + ")"));
        }
        if (j.plusLongueInactivite() >= s.inactiviteMinutes()) {
            l.add(new Signalement("INACTIVITE", "Inactif " + duree(j.plusLongueInactivite()) + " le " + jj(j.date())
                    + " à partir de " + j.debutInactivite().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)));
        }
        if (j.cartesCapturees() > 0) {
            l.add(new Signalement("CARTES_CAPTUREES", j.cartesCapturees() + " carte(s) capturée(s) le " + jj(j.date())));
        }
        return l;
    }

    private Map<String, Gab> gabs() {
        Map<String, Gab> m = new HashMap<>();
        jdbc.sql("select terminal_id, libelle, localisation, agence, actif from gab").query(Gab.class).list()
                .forEach(g -> m.put(g.terminalId(), g));
        return m;
    }

    private static List<Point> parHeure(long[] heures) {
        List<Point> l = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            l.add(new Point(String.format("%02dh", h), heures[h], 0));
        }
        return l;
    }

    private static List<Motif> motifs(Map<String, Integer> refus, Map<String, String> codes) {
        return refus.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new Motif(e.getKey(), codes.getOrDefault(e.getKey(), "Code inconnu"), e.getValue())).toList();
    }

    private static void controlerPeriode(LocalDate du, LocalDate au) {
        if (du == null || au == null || du.isAfter(au)) {
            throw new RegleMetierException("Indiquez une période valide (du … au …).");
        }
        if (ChronoUnit.DAYS.between(du, au) > 366) {
            throw new RegleMetierException("La période est limitée à un an.");
        }
    }

    static double taux(int refuses, int tentatives) {
        return tentatives == 0 ? 0 : Math.round(1000.0 * refuses / tentatives) / 10.0;
    }

    private static String duree(int minutes) {
        return minutes >= 60 ? (minutes / 60) + " h " + String.format("%02d", minutes % 60) : minutes + " min";
    }

    private static String jj(LocalDate d) {
        return String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    private static LocalTime heure(String v, LocalTime defaut) {
        try {
            return v == null ? defaut : LocalTime.parse(v);
        } catch (RuntimeException e) {
            return defaut;
        }
    }

    private static int entier(String v, int defaut) {
        try {
            return v == null ? defaut : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaut;
        }
    }
}
