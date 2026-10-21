package com.rapmonet.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Intégration et consultation des rapports VSS (VX), sur un fichier anonymisé. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_VSS", "MENU_EXPLORATION_VSS", "MENU_PARAMETRAGE_VSS"})
class IntegrationVssIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void vider() {
        jdbc.update("delete from vss_fichier");
        jdbc.update("update vss_type_rapport set integre = true");
    }

    private static byte[] contenu() throws IOException {
        try (InputStream in = IntegrationVssIntegrationTest.class.getResourceAsStream("/fichiers/vss/VX747_synthetique.txt")) {
            return in.readAllBytes();
        }
    }

    private static MockMultipartFile fichier(byte[] contenu) {
        return new MockMultipartFile("fichier", "TEST_VX747.txt", "text/plain", contenu);
    }

    @Test
    void integreEtCalculeLaSynthese() throws Exception {
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integre").value(true))
                .andExpect(jsonPath("$.dateTraitement").value("2026-07-22"))
                .andExpect(jsonPath("$.sreId").value("1000000001"))
                .andExpect(jsonPath("$.synthese.deviseReglement").value("EUR"))
                .andExpect(jsonPath("$.synthese.montantNet").value(-2183.96))
                .andExpect(jsonPath("$.synthese.nbTransactions").value(20))
                .andExpect(jsonPath("$.synthese.deviseCompensation").value("XOF"))
                .andExpect(jsonPath("$.synthese.montantCompensation").value(-1430040))
                .andExpect(jsonPath("$.rapports", hasSize(7)))
                .andExpect(jsonPath("$.anomalies", hasSize(0)));

        mvc.perform(get("/api/ingestion/vss")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/exploration/vss/rapports").param("code", "VSS-120"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].libelleType").value("Valeur d'interchange"));
    }

    @Test
    void consultationDUnRapport() throws Exception {
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf())).andExpect(status().isOk());
        Long id = jdbc.queryForObject("select id from vss_rapport where code = 'VSS-120'", Long.class);
        mvc.perform(get("/api/exploration/vss/rapports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colonnes", hasItem("CLEARING AMOUNT")))
                .andExpect(jsonPath("$.lignes[0].libelle").value("ISSUER TRANSACTIONS"))
                .andExpect(jsonPath("$.lignes[2].libelle").value("ORIGINAL SALE"))
                .andExpect(jsonPath("$.lignes[2].chemin").value("ISSUER TRANSACTIONS > PURCHASE"))
                .andExpect(jsonPath("$.lignes[2].valeurs['RATE TABLE ID'].texte").value("A1358"))
                .andExpect(jsonPath("$.lignes[2].valeurs['CLEARING AMOUNT'].nombre").value(295709))
                .andExpect(jsonPath("$.lignes[2].valeurs['CLEARING AMOUNT'].sens").value("DB"));

        mvc.perform(get("/api/exploration/vss/rapports/{id}/export", id).param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("vss_120_2026-07-22")));
        byte[] pdf = mvc.perform(get("/api/exploration/vss/rapports/{id}/export", id).param("format", "pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void refuseLesDoublonsEtLesFichiersTronques() throws Exception {
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf())).andExpect(status().isOk());
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.anomalies[0].message", containsString("déjà intégré")));

        String texte = new String(contenu(), StandardCharsets.ISO_8859_1);
        byte[] tronque = texte.substring(0, texte.length() / 2).getBytes(StandardCharsets.ISO_8859_1);
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(tronque)).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.anomalies[*].message", hasItem(containsString("tronqué"))));
        assertThat(jdbc.queryForObject("select count(*) from vss_fichier", Integer.class)).isEqualTo(1);
    }

    @Test
    void unRapportDecocheNEstPasEnregistre() throws Exception {
        mvc.perform(put("/api/parametrage/vss/types-rapports/VSS-210").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"integre\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.integre").value(false));
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rapports[?(@.code == 'VSS-210')].ignore").value(true));
        assertThat(jdbc.queryForObject("select count(*) from vss_rapport where code = 'VSS-210'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vss_rapport", Integer.class)).isEqualTo(6);
    }

    @Test
    void annulation() throws Exception {
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf())).andExpect(status().isOk());
        Long id = jdbc.queryForObject("select id from vss_fichier", Long.class);
        mvc.perform(delete("/api/ingestion/vss/{id}", id).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from vss_ligne", Integer.class)).isZero();
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_EXPLORATION_VSS"})
    void consultationSeuleNePeutNiChargerNiParametrer() throws Exception {
        mvc.perform(multipart("/api/ingestion/vss").file(fichier(contenu())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/parametrage/vss/types-rapports/VSS-210").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"integre\":false}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/parametrage/vss/types-rapports")).andExpect(status().isOk());
    }
}
