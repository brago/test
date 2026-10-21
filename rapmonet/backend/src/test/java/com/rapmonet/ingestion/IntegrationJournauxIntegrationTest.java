package com.rapmonet.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Intégration et consultation des journaux GAB, sur un journal NCR anonymisé. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_EJOURNAL", "MENU_EXPLORATION_EJOURNAL"})
class IntegrationJournauxIntegrationTest {

    private static final String NOM = "99990001_99990001_20260625_1.jrn";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void vider() {
        jdbc.update("delete from ej_fichier");
    }

    private static byte[] journal() throws IOException {
        try (InputStream in = IntegrationJournauxIntegrationTest.class.getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            return in.readAllBytes();
        }
    }

    private static MockMultipartFile fichier(String nom, byte[] contenu) {
        return new MockMultipartFile("fichiers", nom, "application/octet-stream", contenu);
    }

    @Test
    void integreUnJournal() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].integre").value(true))
                .andExpect(jsonPath("$[0].terminal").value("99990001"))
                .andExpect(jsonPath("$[0].dateDebut").value("2026-06-25"))
                .andExpect(jsonPath("$[0].nbSessions").value(25))
                .andExpect(jsonPath("$[0].nbOperations").value(31))
                .andExpect(jsonPath("$[0].nbDoublons").value(0))
                .andExpect(jsonPath("$[0].nbEvenements").value(1))
                .andExpect(jsonPath("$[0].anomalies", hasSize(0)));

        mvc.perform(get("/api/exploration/ejournal/operations").param("type", "RETRAIT").param("statut", "DISTRIBUE"))
                .andExpect(jsonPath("$.total", greaterThan(0)))
                .andExpect(jsonPath("$.elements[*].codeReponse", everyItem(is("00"))))
                .andExpect(jsonPath("$.elements[0].libelleReponse").value("Approuvée"))
                .andExpect(jsonPath("$.elements[0].billetsPris").value(true));
        mvc.perform(get("/api/exploration/ejournal/operations").param("codeReponse", "91"))
                .andExpect(jsonPath("$.elements[0].statut").value("REFUSE"))
                .andExpect(jsonPath("$.elements[0].libelleReponse").value("Émetteur indisponible"));
        mvc.perform(get("/api/exploration/ejournal/terminaux")).andExpect(jsonPath("$", hasItem("99990001")));
    }

    @Test
    void extraitDeSessionEtEvenements() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf())).andExpect(status().isOk());
        Long session = jdbc.queryForObject(
                "select session_id from ej_operation where type = 'RETRAIT' and statut = 'DISTRIBUE' order by id limit 1", Long.class);
        mvc.perform(get("/api/exploration/ejournal/sessions/{id}", session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texte", containsString("CASH WITHRAWAL")))
                .andExpect(jsonPath("$.texte", containsString("TRANSACTION END")))
                .andExpect(jsonPath("$.compteurs.coupures[0]").value(5))
                .andExpect(jsonPath("$.operations[*].type", hasItem("RETRAIT")));
        mvc.perform(get("/api/exploration/ejournal/evenements"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actions", hasItem("AJOUT_ESPECES")))
                .andExpect(jsonPath("$[0].especesAjoutees.TYPE1").value(3118));
    }

    @Test
    void journauxQuiSeChevauchentSansDoublon() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf())).andExpect(status().isOk());
        // Même contenu + une ligne : fichier différent, mêmes opérations.
        byte[] copie = (new String(journal(), StandardCharsets.ISO_8859_1) + "\rEJ LOG COPIED OK\r").getBytes(StandardCharsets.ISO_8859_1);
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier("99990001_99990001_20260626_1.jrn", copie)).with(csrf()))
                .andExpect(jsonPath("$[0].integre").value(true))
                .andExpect(jsonPath("$[0].nbOperations").value(0))
                .andExpect(jsonPath("$[0].nbDoublons").value(31));
        assertThat(jdbc.queryForObject("select count(*) from ej_operation", Integer.class)).isEqualTo(31);
    }

    @Test
    void plusieursFichiersTraitesIndependamment() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal")
                        .file(fichier(NOM, journal()))
                        .file(fichier("autre.txt", "ceci n'est pas un journal".getBytes(StandardCharsets.ISO_8859_1)))
                        .file(fichier(NOM, journal()))
                        .with(csrf()))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].integre").value(true))
                .andExpect(jsonPath("$[1].integre").value(false))
                .andExpect(jsonPath("$[2].integre").value(false))
                .andExpect(jsonPath("$[2].anomalies[0].message", containsString("déjà intégré")));
    }

    @Test
    void dateIncoherenteAvecLeNomDuFichier() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier("99990001_99990001_20240101_1.jrn", journal())).with(csrf()))
                .andExpect(jsonPath("$[0].integre").value(true))
                .andExpect(jsonPath("$[0].anomalies[0].message", containsString("format de date")));
    }

    @Test
    void exportEtAnnulation() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/exploration/ejournal/operations/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("operations_gab_")));
        Long id = jdbc.queryForObject("select id from ej_fichier", Long.class);
        mvc.perform(delete("/api/ingestion/ejournal/{id}", id).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from ej_operation", Integer.class)).isZero();
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_EXPLORATION_EJOURNAL"})
    void consultationSeuleNePeutPasCharger() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf()))
                .andExpect(status().isForbidden());
    }
    @Test
    void billetsPresentesValorisesSelonLesCoupures() throws Exception {
        mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf()))
                .andExpect(jsonPath("$[0].anomalies", hasSize(0)));

        // Cassettes 1 à 4 = 10 000, 5 000, 2 000, 1 000 XOF : le montant des billets égale celui du ticket.
        assertThat(jdbc.queryForObject("select count(*) from ej_operation where montant_billets is not null", Integer.class))
                .isEqualTo(19);
        assertThat(jdbc.queryForObject("select count(*) from ej_operation where montant_billets <> montant", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select montant_billets from ej_operation where montant = 75000 and type = 'RETRAIT'",
                Long.class)).isEqualTo(75_000L); // 5 × 10 000 + 3 × 5 000 + 5 × 2 000
        mvc.perform(get("/api/exploration/ejournal/operations").param("type", "RETRAIT").param("statut", "DISTRIBUE"))
                .andExpect(jsonPath("$.elements[0].montantBillets").isNumber())
                .andExpect(jsonPath("$.elements[0].ecartBillets").value(0));
        mvc.perform(get("/api/exploration/ejournal/operations").param("ecartBillets", "true"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void ecartEntreBilletsEtTicketSignale() throws Exception {
        jdbc.update("update parametre_application set valeur = '10000,5000,1000,1000' where cle = 'ejournal.coupures'");
        try {
            mvc.perform(multipart("/api/ingestion/ejournal").file(fichier(NOM, journal())).with(csrf()))
                    .andExpect(jsonPath("$[0].integre").value(true))
                    .andExpect(jsonPath("$[0].anomalies[0].gravite").value("AVERTISSEMENT"))
                    .andExpect(jsonPath("$[0].anomalies[*].message", hasItem(containsString(
                            "montant du ticket 75 000, billets présentés 70 000 (5 × 10 000 + 3 × 5 000 + 5 × 1 000) – écart de -5 000"))));
            mvc.perform(get("/api/exploration/ejournal/operations").param("ecartBillets", "true"))
                    .andExpect(jsonPath("$.total", greaterThan(0)))
                    .andExpect(jsonPath("$.elements[0].ecartBillets", lessThan(0)));
        } finally {
            jdbc.update("update parametre_application set valeur = '10000,5000,2000,1000' where cle = 'ejournal.coupures'");
        }
    }
}
