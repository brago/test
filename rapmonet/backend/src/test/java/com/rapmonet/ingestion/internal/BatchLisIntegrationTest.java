package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.BatchJournaux;
import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chargement automatique des fichiers LIS déposés dans un dossier (fichiers synthétiques). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_CHARGEMENT_LIS"})
class BatchLisIntegrationTest {

    /** Journée 29/12/2025 = jour 363 de 2025. */
    private static final String NOM = "LIS_IN_TEST_SW_25363_01";

    @Autowired
    BatchLisService batch;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path racine;

    Path entree;
    Path archive;
    Path rejet;

    @BeforeEach
    void preparer() {
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("delete from batch_execution");
        entree = racine.resolve("entree");
        archive = racine.resolve("archive");
        rejet = racine.resolve("rejet");
        batch.modifier(new BatchJournaux.Configuration(true, "08:00", entree.toString(), archive.toString(),
                rejet.toString()), "test");
    }

    @AfterEach
    void restaurer() {
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("delete from batch_execution");
        batch.modifier(new BatchJournaux.Configuration(true, "08:00", "donnees/lis/entree", "donnees/lis/archive",
                "donnees/lis/rejet"), "test");
    }

    private static byte[] sw() {
        return FichierLisSynthetique.creer("291225", "001", List.of(
                new Operation("07", "1", "4598470000001234", 50000, "74" + "0".repeat(19) + "01", "000000000001",
                        "AUTRE001", "281225", null, null, "00008800", "49999900")));
    }

    @Test
    void integreEtArchiveParMois() throws Exception {
        Files.createDirectories(entree);
        Files.write(entree.resolve(NOM), sw());

        BatchJournaux.Execution ex = batch.executer("PLANIFIE", "BATCH");

        assertThat(ex.statut()).as(ex.toString()).isEqualTo("TERMINE");
        assertThat(ex.nbIntegres()).isEqualTo(1);
        assertThat(ex.nbOperations()).isEqualTo(1);
        assertThat(ex.detail().getFirst().terminal()).isEqualTo("SW 2025-12-29");
        assertThat(archive.resolve("2025-12").resolve(NOM)).exists();
        assertThat(jdbc.queryForObject("select type_lis from lis_fichier", String.class)).isEqualTo("SW");
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'BATCH_LIS'"
                + " and horodatage > now() - interval '1 minute'", Integer.class)).isPositive();
    }

    @Test
    void fichierDejaIntegreArchiveSansDoublon() throws Exception {
        Files.createDirectories(entree);
        Files.write(entree.resolve(NOM), sw());
        batch.executer("MANUEL", "admin");
        Files.write(entree.resolve(NOM), sw());

        BatchJournaux.Execution ex = batch.executer("MANUEL", "admin");

        assertThat(ex.statut()).isEqualTo("TERMINE");
        assertThat(ex.nbDejaIntegres()).isEqualTo(1);
        assertThat(archive.resolve("2025-12").resolve(NOM + ".1")).exists();
        assertThat(jdbc.queryForObject("select count(*) from lis_fichier", Integer.class)).isEqualTo(1);
    }

    @Test
    void nomSansTypeMisEnRejet() throws Exception {
        Files.createDirectories(entree);
        Files.write(entree.resolve("fichier_inconnu.txt"), sw());

        BatchJournaux.Execution ex = batch.executer("MANUEL", "admin");

        assertThat(ex.statut()).isEqualTo("TERMINE_AVEC_REJETS");
        assertThat(ex.detail().getFirst().message()).contains("Type SW ou PR introuvable");
        try (var f = Files.walk(rejet)) {
            assertThat(f.filter(p -> p.getFileName().toString().endsWith(".erreur.txt")).toList()).hasSize(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from lis_fichier", Integer.class)).isZero();
    }

    @Test
    void etatConsultableAvecLeMenuChargementLis() throws Exception {
        mvc.perform(get("/api/ingestion/lis/batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.heure").value("08:00"))
                .andExpect(jsonPath("$.enCours").value(false));
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_EJOURNAL"})
    void reserveAuMenuChargementLis() throws Exception {
        mvc.perform(get("/api/ingestion/lis/batch")).andExpect(status().isForbidden());
    }
}
