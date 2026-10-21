package com.rapmonet.gab;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.Operation;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.TypeLis;
import org.junit.jupiter.api.AfterEach;
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

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Table des BIN GIM (fichier synthétique LIS_IN_BIN_TEST_01) et contrôle des BIN de la banque. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TableBinsGimIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IntegrationLis lis;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("delete from bin_carte");
        jdbc.update("delete from bin_gim");
        jdbc.update("delete from bin_gim_import");
    }

    private MockMultipartFile fichier() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/bin/LIS_IN_BIN_TEST_01")) {
            return new MockMultipartFile("fichier", "LIS_IN_BIN_TEST_01", "text/plain", in.readAllBytes());
        }
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_GAB"})
    void importEtRecherche() throws Exception {
        mvc.perform(multipart("/api/parametrage/gab/bins/table-gim").file(fichier()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plages").value(4))
                .andExpect(jsonPath("$.lignesIgnorees").value(2))
                .andExpect(jsonPath("$.anomalies[0]").value("Ligne 5 : plage invalide"))
                .andExpect(jsonPath("$.anomalies[1]").value("Ligne 7 : début de plage après la fin"));
        mvc.perform(get("/api/parametrage/gab/bins/table-gim"))
                .andExpect(jsonPath("$.fichier").value("LIS_IN_BIN_TEST_01"))
                .andExpect(jsonPath("$.plages").value(4))
                .andExpect(jsonPath("$.parPays[0].libellePays").value("Côte d'Ivoire"))
                .andExpect(jsonPath("$.parPays[0].plages").value(2));
        mvc.perform(get("/api/parametrage/gab/bins/table-gim/plages").param("recherche", "999999"))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/parametrage/gab/bins/table-gim/plages").param("recherche", "999999").param("pays", "686"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].prefixe").value("99999922"))
                .andExpect(jsonPath("$[0].libellePays").value("Sénégal"));

        // Un second import remplace la table.
        mvc.perform(multipart("/api/parametrage/gab/bins/table-gim").file(fichier()).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from bin_gim", Integer.class)).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_GAB"})
    void fichierSansPlageRefuse() throws Exception {
        mvc.perform(multipart("/api/parametrage/gab/bins/table-gim")
                        .file(new MockMultipartFile("fichier", "autre.txt", "text/plain", "bonjour;le;monde".getBytes()))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser(username = "consultant", authorities = {"UTILISATEUR", "MENU_GAB"})
    void importReserveAuParametrage() throws Exception {
        mvc.perform(multipart("/api/parametrage/gab/bins/table-gim").file(fichier()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_GAB"})
    void controleDesBinsEtPropositions() throws Exception {
        mvc.perform(multipart("/api/parametrage/gab/bins/table-gim").file(fichier()).with(csrf())).andExpect(status().isOk());
        // La banque a la sous-plage 99999911 du préfixe partagé 999999.
        jdbc.update("insert into bin_carte (bin, libelle, type_carte) values ('99999911', 'GIM', 'DEBIT')");
        mvc.perform(get("/api/parametrage/gab/bins/controles"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plagesCouvertes").value(1))
                .andExpect(jsonPath("$[0].pays[0]").value("Côte d'Ivoire"))
                .andExpect(jsonPath("$[0].autresPlagesDuPrefixe").value(2))
                .andExpect(jsonPath("$[0].paysAutresPlages", hasSize(2)));

        // Les cartes 499999 apparaissent dans les LIS émission : BIN proposé, non partagé.
        byte[] sw = FichierLisSynthetique.creer("100426", "001", List.of(
                new Operation("07", "1", "4999990000000001", 20000, "74" + "0".repeat(19) + "01", "000000000001", "AUTRE001",
                        "150326", null, null, "00008800", "49999900"),
                new Operation("05", "1", "4999990000000002", 15000, "74" + "0".repeat(19) + "02", "000000000002", "AUTRE001",
                        "150326", null, null, "00008800", "49999900")));
        var r = lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_SW_26100_01", TypeLis.SW, sw)), "test");
        assertThat(r.integre()).as(r.toString()).isTrue();
        mvc.perform(get("/api/parametrage/gab/bins/propositions"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bin").value("499999"))
                .andExpect(jsonPath("$[0].operations").value(2))
                .andExpect(jsonPath("$[0].plagesGim").value(1))
                .andExpect(jsonPath("$[0].prefixePartage").value(false));

        jdbc.update("insert into bin_carte (bin, libelle, type_carte) values ('499999', 'Visa', 'DEBIT')");
        mvc.perform(get("/api/parametrage/gab/bins/propositions")).andExpect(jsonPath("$", hasSize(0)));
    }
}
