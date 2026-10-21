package com.rapmonet.reporting.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.gab.BinsBanque;
import com.rapmonet.gab.ReferentielGab;
import com.rapmonet.ingestion.StatistiquesActivite;
import com.rapmonet.ingestion.StatistiquesActivite.Agregat;
import com.rapmonet.ingestion.StatistiquesActivite.Source;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Indicateurs de performance en volume (GAB, TPE, cartes) par mois, et base trimestrielle des états
 * réglementaires (émission et acquisition par réseau).
 */
@Service
public class IndicateursService {

    public enum Axe { GAB, TPE, CARTE }

    /** Indicateurs proposés : axe, libellé, source des données. */
    public enum Indicateur {
        GAB_RETRAITS_JOURNAUX(Axe.GAB, "Retraits distribués par nos GAB (toutes cartes)",
                "Journaux électroniques : retraits dont les billets ont été pris.", Source.JOURNAUX_RETRAITS),
        GAB_RETRAITS_CONFRERES(Axe.GAB, "Retraits de cartes d'autres banques sur nos GAB",
                "Fichiers LIS : avis d'acquisition (TC 49), code commerçant 6011.", Source.LIS_ACQUISITION_RETRAITS),
        TPE_ACHATS_CONFRERES(Axe.TPE, "Achats de cartes d'autres banques sur nos TPE",
                "Fichiers LIS : avis d'acquisition (TC 49), codes commerçant autres que 6011.", Source.LIS_ACQUISITION_ACHATS),
        CARTE_RETRAITS_AUTRES_GAB(Axe.CARTE, "Retraits de nos cartes sur les GAB d'autres banques",
                "Fichiers LIS du switch : retraits (TC 07) en première présentation, par BIN.", Source.LIS_EMISSION_RETRAITS),
        CARTE_ACHATS(Axe.CARTE, "Achats de nos cartes chez les commerçants",
                "Fichiers LIS du switch : achats (TC 05) en première présentation, par BIN.", Source.LIS_EMISSION_ACHATS),
        CARTE_RETRAITS_NOS_GAB(Axe.CARTE, "Retraits de nos cartes sur nos GAB",
                "Journaux électroniques : retraits des cartes dont le BIN est déclaré, par BIN.", Source.JOURNAUX_RETRAITS_PAR_BIN);

        public final Axe axe;
        public final String libelle;
        public final String description;
        final Source source;

        Indicateur(Axe axe, String libelle, String description, Source source) {
            this.axe = axe;
            this.libelle = libelle;
            this.description = description;
            this.source = source;
        }
    }

    public record DescriptionIndicateur(String code, Axe axe, String libelle, String description) {
    }

    /** Une entité (GAB, TPE ou BIN) : valeurs par mois dans l'ordre des mois du résultat. */
    public record Ligne(String entite, String libelle, List<Long> nombres, List<Long> montants, long totalNombre,
                        long totalMontant) {
    }

    public record Kpi(String indicateur, String libelle, String description, Axe axe, List<YearMonth> mois,
                      List<Ligne> lignes, List<Long> totauxNombre, List<Long> totauxMontant, long totalNombre,
                      long totalMontant, String avertissement) {
    }

    /** Une rubrique de l'état trimestriel. @param entites nombre de GAB, TPE ou BIN concernés */
    public record LigneTrimestre(String section, String rubrique, String reseau, long nombre, long montant, long entites) {
    }

    public record EtatTrimestriel(int annee, int trimestre, LocalDate du, LocalDate au, List<LigneTrimestre> lignes,
                                  String avertissement) {
    }

    private static final int MOIS_MAX = 36;

    private final StatistiquesActivite statistiques;
    private final ReferentielGab referentiel;
    private final BinsBanque bins;

    IndicateursService(StatistiquesActivite statistiques, ReferentielGab referentiel, BinsBanque bins) {
        this.statistiques = statistiques;
        this.referentiel = referentiel;
        this.bins = bins;
    }

    public List<DescriptionIndicateur> indicateurs() {
        return Arrays.stream(Indicateur.values())
                .map(i -> new DescriptionIndicateur(i.name(), i.axe, i.libelle, i.description)).toList();
    }

    public Kpi kpi(String code, YearMonth du, YearMonth au) {
        Indicateur indicateur;
        try {
            indicateur = Indicateur.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RegleMetierException("Indicateur inconnu : " + code);
        }
        if (du == null || au == null || au.isBefore(du)) {
            throw new RegleMetierException("Période invalide : le mois de fin doit suivre le mois de début.");
        }
        if (ChronoUnit.MONTHS.between(du, au) >= MOIS_MAX) {
            throw new RegleMetierException("Période limitée à " + MOIS_MAX + " mois.");
        }
        List<YearMonth> mois = new ArrayList<>();
        for (YearMonth m = du; !m.isAfter(au); m = m.plusMonths(1)) {
            mois.add(m);
        }
        List<Agregat> agregats = donnees(indicateur, du.atDay(1), au.atEndOfMonth());
        Map<String, String> libelles = indicateur.axe == Axe.CARTE ? referentiel.produitsBin() : referentiel.libellesGab();

        Map<String, List<Agregat>> parEntite = agregats.stream()
                .collect(Collectors.groupingBy(Agregat::entite, TreeMap::new, Collectors.toList()));
        List<Ligne> lignes = new ArrayList<>();
        parEntite.forEach((entite, l) -> {
            long[] n = new long[mois.size()];
            long[] mt = new long[mois.size()];
            for (Agregat a : l) {
                int i = mois.indexOf(a.mois());
                if (i >= 0) {
                    n[i] += a.nombre();
                    mt[i] += a.montant();
                }
            }
            // Un BIN n'a pour libellé que le produit déclaré (le nom présent dans le LIS est celui du commerçant).
            String libelle = libelles.getOrDefault(entite, indicateur.axe == Axe.CARTE ? null : l.stream()
                    .map(Agregat::libelle).filter(x -> x != null && !x.isBlank()).findFirst().map(String::strip).orElse(null));
            lignes.add(new Ligne(entite, libelle, Arrays.stream(n).boxed().toList(), Arrays.stream(mt).boxed().toList(),
                    Arrays.stream(n).sum(), Arrays.stream(mt).sum()));
        });
        lignes.sort(Comparator.comparingLong(Ligne::totalMontant).reversed().thenComparing(Ligne::entite));
        List<Long> totauxN = new ArrayList<>();
        List<Long> totauxM = new ArrayList<>();
        for (int i = 0; i < mois.size(); i++) {
            int k = i;
            totauxN.add(lignes.stream().mapToLong(x -> x.nombres().get(k)).sum());
            totauxM.add(lignes.stream().mapToLong(x -> x.montants().get(k)).sum());
        }
        String avertissement = null;
        if (indicateur.axe == Axe.CARTE && !bins.binsParametres()) {
            avertissement = "Aucun BIN de la banque n'est déclaré : les libellés produits sont absents"
                    + (indicateur == Indicateur.CARTE_RETRAITS_NOS_GAB ? " et cet indicateur est vide." : ".");
        } else if (lignes.isEmpty()) {
            avertissement = "Aucune donnée intégrée pour cette période.";
        }
        return new Kpi(indicateur.name(), indicateur.libelle, indicateur.description, indicateur.axe, mois, lignes, totauxN,
                totauxM, totauxN.stream().mapToLong(Long::longValue).sum(), totauxM.stream().mapToLong(Long::longValue).sum(),
                avertissement);
    }

    private List<Agregat> donnees(Indicateur indicateur, LocalDate du, LocalDate au) {
        List<Agregat> l = statistiques.agreger(indicateur.source, du, au);
        // Cartes de la banque : restreint aux BIN déclarés (pour les LIS, seulement s'il y en a).
        if (indicateur == Indicateur.CARTE_RETRAITS_NOS_GAB || (indicateur.axe == Axe.CARTE && bins.binsParametres())) {
            return deLaBanque(l);
        }
        return l;
    }

    private List<Agregat> deLaBanque(List<Agregat> l) {
        return l.stream().filter(a -> bins.carteDeLaBanque(a.entite())).toList();
    }

    private List<Agregat> emission(Source source, LocalDate du, LocalDate au) {
        List<Agregat> l = statistiques.agreger(source, du, au);
        return bins.binsParametres() ? deLaBanque(l) : l;
    }

    // ------------------------------------------------------------------ état trimestriel

    public EtatTrimestriel trimestre(int annee, int trimestre) {
        if (trimestre < 1 || trimestre > 4 || annee < 2000 || annee > 2100) {
            throw new RegleMetierException("Trimestre attendu : 1 à 4, année sur quatre chiffres.");
        }
        LocalDate du = LocalDate.of(annee, (trimestre - 1) * 3 + 1, 1);
        LocalDate au = du.plusMonths(3).minusDays(1);
        List<LigneTrimestre> l = new ArrayList<>();
        ajouter(l, "Émission (cartes de la banque)", "Retraits sur les GAB d'autres banques",
                emission(Source.LIS_EMISSION_RETRAITS, du, au));
        ajouter(l, "Émission (cartes de la banque)", "Achats chez les commerçants",
                emission(Source.LIS_EMISSION_ACHATS, du, au));
        ajouter(l, "Émission (cartes de la banque)", "Retraits sur les GAB de la banque (on-us)",
                deLaBanque(statistiques.agreger(Source.JOURNAUX_RETRAITS_PAR_BIN, du, au)));
        ajouter(l, "Acquisition (GAB et TPE de la banque)", "Retraits de cartes d'autres banques sur nos GAB",
                statistiques.agreger(Source.LIS_ACQUISITION_RETRAITS, du, au));
        ajouter(l, "Acquisition (GAB et TPE de la banque)", "Achats de cartes d'autres banques sur nos TPE",
                statistiques.agreger(Source.LIS_ACQUISITION_ACHATS, du, au));
        ajouter(l, "Acquisition (GAB et TPE de la banque)", "Retraits distribués par nos GAB, toutes cartes",
                statistiques.agreger(Source.JOURNAUX_RETRAITS, du, au));
        return new EtatTrimestriel(annee, trimestre, du, au, l,
                "Données de base des états réglementaires, calculées sur les fichiers intégrés. Les formats officiels "
                        + "BCEAO (BP4) et VISA (Issuing, Acquiring) seront produits à réception des modèles.");
    }

    private static void ajouter(List<LigneTrimestre> l, String section, String rubrique, List<Agregat> agregats) {
        Map<String, List<Agregat>> parReseau = agregats.stream().collect(Collectors.groupingBy(
                a -> a.reseau() == null ? "Journaux GAB" : a.reseau(), LinkedHashMap::new, Collectors.toList()));
        if (parReseau.isEmpty()) {
            l.add(new LigneTrimestre(section, rubrique, "–", 0, 0, 0));
        }
        parReseau.forEach((reseau, x) -> l.add(new LigneTrimestre(section, rubrique, reseau,
                x.stream().mapToLong(Agregat::nombre).sum(), x.stream().mapToLong(Agregat::montant).sum(),
                x.stream().map(Agregat::entite).collect(Collectors.toSet()).size())));
    }
}
