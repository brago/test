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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Intégration de fichiers LIS de bout en bout, avec le paramétrage LIS 3.038 réel. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_LIS", "MENU_EXPLORATION_LIS"})
class IntegrationLisIntegrationTest {

    private static final String CARTE = "4999990000001234";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void viderLesDonnees() {
        jdbc.update("delete from lis_fichier");
    }

    private static MockMultipartFile sw(byte[] contenu) {
        return new MockMultipartFile("sw", "LIS_IN_TEST_SW_23041_01", "application/octet-stream", contenu);
    }

    private static byte[] fichier() {
        return FichierLisSynthetique.creer("100223", "001", new String[][]{{CARTE, "50000"}, {"5999990000005678", "20000"}});
    }

    @Test
    void integreUnFichierEtMasqueLesNumerosDeCarte() throws Exception {
        mvc.perform(multipart("/api/ingestion/lis").file(sw(fichier())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integre").value(true))
                .andExpect(jsonPath("$.fichiers[0].journee").value("2023-02-10"))
                .andExpect(jsonPath("$.fichiers[0].nbTransactions").value(2))
                .andExpect(jsonPath("$.fichiers[0].repartitionTc.07").value(2))
                .andExpect(jsonPath("$.fichiers[0].anomalies", hasSize(0)));

        mvc.perform(get("/api/exploration/lis/transactions").param("tc", "07").param("du", "2023-02-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].sousFichier").value("FL"))
                .andExpect(jsonPath("$.elements[0].libelleTc").value("Retrait (débit porteur)"))
                .andExpect(jsonPath("$.elements[0].attributs.LIS_SOURCE_AMOUNT").value(50000))
                .andExpect(jsonPath("$.elements[0].attributs.LIS_TRANSACTION_DATE").value("2023-02-10"))
                .andExpect(jsonPath("$.elements[0].attributs.LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION")
                        .value("499999******1234"));

        Integer enClair = jdbc.queryForObject("""
                select (select count(*) from lis_enregistrement where contenu like ?)
                     + (select count(*) from lis_transaction where attributs::text like ?)""",
                Integer.class, "%" + CARTE + "%", "%" + CARTE + "%");
        assertThat(enClair).isZero();
    }

    @Test
    void refuseUnFichierDejaIntegre() throws Exception {
        mvc.perform(multipart("/api/ingestion/lis").file(sw(fichier())).with(csrf())).andExpect(status().isOk());
        mvc.perform(multipart("/api/ingestion/lis").file(sw(fichier())).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.integre").value(false))
                .andExpect(jsonPath("$.fichiers[0].anomalies[0].message", containsString("déjà intégré")));
    }

    @Test
    void refuseUnFichierTronqueSansRienEnregistrer() throws Exception {
        String texte = new String(fichier(), java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] tronque = texte.substring(0, texte.lastIndexOf("91")).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        mvc.perform(multipart("/api/ingestion/lis").file(sw(tronque)).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fichiers[0].anomalies[*].message", org.hamcrest.Matchers.hasItem(containsString("Fin de fichier absente"))));
        assertThat(jdbc.queryForObject("select count(*) from lis_fichier", Integer.class)).isZero();
    }

    @Test
    void detailEtAnnulation() throws Exception {
        mvc.perform(multipart("/api/ingestion/lis").file(sw(fichier())).with(csrf())).andExpect(status().isOk());
        Long transaction = jdbc.queryForObject("select min(id) from lis_transaction", Long.class);
        Long fichierId = jdbc.queryForObject("select id from lis_fichier", Long.class);

        mvc.perform(get("/api/exploration/lis/transactions/{id}", transaction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes", hasSize(2)))
                .andExpect(jsonPath("$.lignes[0].tcr").value("0"))
                .andExpect(jsonPath("$.lignes[0].contenu", containsString("499999******1234")));

        mvc.perform(get("/api/ingestion/lis")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/ingestion/lis/{id}", fichierId).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/ingestion/lis")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/exploration/lis/transactions/{id}", transaction)).andExpect(status().isNotFound());
    }

    @Test
    void exportExcelEtPdf() throws Exception {
        mvc.perform(multipart("/api/ingestion/lis").file(sw(fichier())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/exploration/lis/transactions/export").param("format", "xlsx").param("tc", "07"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("transactions_lis_")));
        byte[] pdf = mvc.perform(get("/api/exploration/lis/transactions/export").param("format", "pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        mvc.perform(get("/api/exploration/lis/transactions/export").param("format", "csv"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_EXPLORATION_LIS"})
    void chargementInterditSansLeMenu() throws Exception {
        mvc.perform(multipart("/api/ingestion/lis").file(sw(fichier())).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void parametrageLisDisponible() throws Exception {
        mvc.perform(get("/api/parametrage/formats/LIS_GIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sousFichiers[*].code", org.hamcrest.Matchers.contains("FC", "FV", "FM", "FL", "FS")))
                .andExpect(jsonPath("$.tcs.07.tcrs", hasSize(3)))
                .andExpect(jsonPath("$.tcs.07.tcrs[0].attributs[0].position").value(10));
    }
}
