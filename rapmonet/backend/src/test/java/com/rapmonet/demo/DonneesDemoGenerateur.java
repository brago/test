package com.rapmonet.demo;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.AvisAcquisition;
import com.rapmonet.ingestion.IntegrationJournaux;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fabrique le jeu de démonstration (dossier demo/) : journal GAB anonymisé et fichier LIS SW synthétique cohérent.
 * Lancement : ./mvnw test -Dtest=DonneesDemoGenerateur -Ddemo.sortie=../demo
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "demo.sortie", matches = ".+")
class DonneesDemoGenerateur {

    private static final DateTimeFormatter JJMMAA = DateTimeFormatter.ofPattern("ddMMyy");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    @Autowired
    IntegrationJournaux journaux;

    @Autowired
    JdbcTemplate jdbc;

    private record Retrait(String carte, long montant, LocalDateTime dateHeure, String reference, String compte) {
    }

    @Test
    void generer() throws Exception {
        jdbc.update("delete from ej_fichier");
        // Journal de démonstration : le journal anonymisé, dont les tickets des comptes fictifs n° 2 et 3 n'impriment plus de
        // compte (cartes d'autres banques).
        byte[] journal;
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            journal = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1)
                    .replace("ACCOUNT :0999000000000003", "ACCOUNT :").replace("ACCOUNT :0999000000000002", "ACCOUNT :").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        assertThat(journaux.integrer("99990001_20260625.jrn", journal, "demo").integre()).isTrue();
        List<Retrait> retraits = jdbc.query("""
                        select carte, montant, date_heure, reference, compte from ej_operation
                        where type = 'RETRAIT' and statut = 'DISTRIBUE' order by date_heure""",
                (rs, n) -> new Retrait(rs.getString(1), rs.getLong(2), rs.getTimestamp(3).toLocalDateTime(),
                        rs.getString(4), rs.getString(5)));
        jdbc.update("delete from ej_fichier");
        List<Retrait> confreres = retraits.stream().filter(r -> r.compte() == null || r.compte().isBlank()).toList();
        System.out.println("Retraits distribués : " + retraits.size() + ", sans compte (confrères) : " + confreres.size());
        assertThat(confreres).hasSizeGreaterThanOrEqualTo(4);

        List<AvisAcquisition> avis = new ArrayList<>();
        for (int i = 0; i < confreres.size(); i++) {
            Retrait r = confreres.get(i);
            if (i == 1) {
                continue; // retrait non remonté par le GIM
            }
            long montant = i == 2 ? r.montant() + 500 : r.montant(); // montant LIS différent (règle de tolérance)
            avis.add(AvisAcquisition.retrait(carte(r), montant, "99990001", JJMMAA.format(r.dateHeure()),
                    HHMMSS.format(r.dateHeure().plusMinutes(2)), i == 3 ? r.reference() : "D" + String.format("%05d", i)));
        }
        LocalDateTime jour = confreres.get(0).dateHeure();
        // Avis sans retrait au journal, et avis d'un GAB dont le journal n'est pas chargé.
        avis.add(AvisAcquisition.retrait("4111110000001111", 20000, "99990001", JJMMAA.format(jour), "101010", "DZZ999"));
        avis.add(AvisAcquisition.retrait("4111110000002222", 30000, "99990009", JJMMAA.format(jour), "111111", "DYY888"));

        byte[] contenu = FichierLisSynthetique.creer(JJMMAA.format(jour.toLocalDate().plusDays(5)), "001", List.of(), avis);
        Path sortie = Path.of(System.getProperty("demo.sortie"));
        Files.createDirectories(sortie);
        Files.write(sortie.resolve("99990001_20260625.jrn"), journal);
        Path f = sortie.resolve("LIS_IN_DEMO_SW_" + String.format("%03d", jour.getDayOfYear() + 5) + "26_01");
        Files.write(f, contenu);
        System.out.println("Écrit : " + f.toAbsolutePath() + " (" + avis.size() + " avis)");
    }

    /** Carte de démonstration : 6 premiers et 4 derniers chiffres du ticket, milieu à zéro. */
    private static String carte(Retrait r) {
        String c = r.carte().replaceAll("\\D", "");
        return c.substring(0, 6) + "000000" + c.substring(c.length() - 4);
    }
}
