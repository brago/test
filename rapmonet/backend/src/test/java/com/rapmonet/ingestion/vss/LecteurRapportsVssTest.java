package com.rapmonet.ingestion.vss;

import com.rapmonet.ingestion.moteur.Gravite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LecteurRapportsVssTest {

    private static List<String> lignes() throws IOException {
        try (InputStream in = LecteurRapportsVssTest.class.getResourceAsStream("/fichiers/vss/VX747_synthetique.txt")) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).split("\\r?\\n", -1));
        }
    }

    private static RapportVss rapport(ResultatLectureVss r, String code) {
        return r.rapports().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
    }

    /** Première ligne portant des valeurs avec ce libellé (les titres de section n'en ont pas). */
    private static LigneVss ligne(RapportVss r, String libelle) {
        return r.lignes().stream().filter(l -> l.libelle().equals(libelle) && !l.valeurs().isEmpty()).findFirst().orElseThrow();
    }

    @Test
    void litTousLesRapportsEtLeursEntetes() throws IOException {
        ResultatLectureVss r = new LecteurRapportsVss().lire(lignes());

        assertThat(r.anomalies()).isEmpty();
        assertThat(r.rapports()).extracting(RapportVss::code)
                .containsExactly("VSS-110", "VSS-120", "VSS-130", "VSS-140", "VSS-210", "VSS-300", "VSS-900-S");
        RapportVss v110 = rapport(r, "VSS-110");
        assertThat(v110.titre()).isEqualTo("SETTLEMENT SUMMARY REPORT");
        assertThat(v110.sreId()).isEqualTo("1000000001");
        assertThat(v110.sreNom()).isEqualTo("BANQUE ESSAI");
        assertThat(v110.rollup()).isEqualTo("9000000002 SUMM TEST");
        assertThat(v110.dateTraitement()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(v110.colonnes()).containsExactly("COUNT", "CREDIT AMOUNT", "DEBIT AMOUNT", "TOTAL AMOUNT");
        assertThat(r.rapports()).allMatch(RapportVss::complet);
    }

    @Test
    void montantsAvecSensEtDevises() throws IOException {
        RapportVss v110 = rapport(new LecteurRapportsVss().lire(lignes()), "VSS-110");
        LigneVss net = ligne(v110, "NET SETTLEMENT AMOUNT");
        assertThat(net.chemin()).isEqualTo("TOTAL");
        assertThat(net.deviseReglement()).isEqualTo("EUR");
        assertThat(net.valeurs().get("TOTAL AMOUNT")).isEqualTo(new ValeurVss(new BigDecimal("2183.96"), "DB", null));
        assertThat(net.valeurs().get("TOTAL AMOUNT").signe()).isEqualByComparingTo("-2183.96");
        assertThat(ligne(v110, "TOTAL INTERCHANGE VALUE").valeurs().get("COUNT").nombre()).isEqualByComparingTo("20");
    }

    @Test
    void hierarchieEtLignesDeSuite() throws IOException {
        RapportVss v120 = rapport(new LecteurRapportsVss().lire(lignes()), "VSS-120");
        assertThat(v120.lignes().getFirst().libelle()).isEqualTo("ISSUER TRANSACTIONS");
        assertThat(v120.lignes().getFirst().valeurs()).isEmpty();
        List<LigneVss> ventes = v120.lignes().stream().filter(l -> l.libelle().equals("ORIGINAL SALE")).toList();
        // Une ligne par table de taux, la 2e et la 3e reprennent le libellé de la précédente.
        assertThat(ventes).extracting(l -> l.valeurs().get("RATE TABLE ID").texte()).containsExactly("A1358", "A1359", "A1360");
        assertThat(ventes.getFirst().chemin()).isEqualTo("ISSUER TRANSACTIONS > PURCHASE");
        assertThat(ventes.getFirst().deviseCompensation()).isEqualTo("XOF");
        assertThat(ventes.getFirst().valeurs().get("CLEARING AMOUNT")).isEqualTo(new ValeurVss(new BigDecimal("295709"), "DB", null));
        assertThat(ligne(v120, "NET PURCHASE").valeurs()).containsOnlyKeys("INTERCHANGE VALUE DEBITS");
    }

    @Test
    void rapportSurPlusieursPagesEtRapportVide() throws IOException {
        ResultatLectureVss r = new LecteurRapportsVss().lire(lignes());
        RapportVss v140 = rapport(r, "VSS-140");
        assertThat(v140.nbPages()).isEqualTo(2);
        assertThat(ligne(v140, "TOTAL ISSUER CHARGES").page()).isEqualTo(2);
        assertThat(ligne(v140, "TOTAL ISSUER CHARGES").valeurs().get("VISA CHARGES DEBITS").nombre()).isEqualByComparingTo("26.91");
        RapportVss v300 = rapport(r, "VSS-300");
        assertThat(v300.sansDonnees()).isTrue();
        assertThat(v300.lignes()).allMatch(l -> l.valeurs().isEmpty());
    }

    @Test
    void colonnesRegroupeesSousUneBanniere() throws IOException {
        RapportVss v210 = rapport(new LecteurRapportsVss().lire(lignes()), "VSS-210");
        assertThat(v210.colonnes()).contains("INTERCHANGE AMOUNT (SETTLEMENT CURRENCY)", "INTERCHANGE AMOUNT (CLEARING CURRENCY)");
        // Libellé seul sur une ligne, valeurs sur la suivante : une seule ligne.
        assertThat(v210.lignes().stream().filter(l -> l.libelle().equals("TOTAL ORIGINAL SALE")).count()).isEqualTo(1);
        assertThat(ligne(v210, "TOTAL ORIGINAL SALE").valeurs().get("INTERCHANGE AMOUNT (CLEARING CURRENCY)").nombre())
                .isEqualByComparingTo("292843");
    }

    @Test
    void deviseDeCompensationParPage() throws IOException {
        RapportVss v900 = rapport(new LecteurRapportsVss().lire(lignes()), "VSS-900-S");
        assertThat(v900.nbPages()).isEqualTo(6);
        assertThat(v900.lignes()).extracting(LigneVss::deviseCompensation).contains("XOF", "NONE", "ALL");
    }

    @Test
    void fichierTronqueEstBloquant() throws IOException {
        List<String> l = lignes();
        ResultatLectureVss r = new LecteurRapportsVss().lire(l.subList(0, 120));
        assertThat(r.estBloquant()).isTrue();
        assertThat(r.anomalies()).anyMatch(a -> a.gravite() == Gravite.BLOQUANTE && a.message().contains("tronqué"));
    }

    @Test
    void fichierSansRapportEstBloquant() {
        ResultatLectureVss r = new LecteurRapportsVss().lire(List.of(" ceci n'est pas un rapport VSS", ""));
        assertThat(r.estBloquant()).isTrue();
    }

    @Test
    void lectureDesNombresEtDates() {
        assertThat(LecteurRapportsVss.valeur("1,430,040DB")).isEqualTo(new ValeurVss(new BigDecimal("1430040"), "DB", null));
        assertThat(LecteurRapportsVss.valeur("9.62CR").signe()).isEqualByComparingTo("9.62");
        assertThat(LecteurRapportsVss.valeur("A1359").texte()).isEqualTo("A1359");
        assertThat(LecteurRapportsVss.date("22JUL26")).isEqualTo(LocalDate.of(2026, 7, 22));
    }
}
