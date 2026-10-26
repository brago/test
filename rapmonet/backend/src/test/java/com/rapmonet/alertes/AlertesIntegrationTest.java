package com.rapmonet.alertes;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alertes de l'accueil : calculées à la demande, filtrées sur les menus de l'utilisateur. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertesIntegrationTest {

    private static final DateTimeFormatter JJMMAA = DateTimeFormatter.ofPattern("ddMMyy");

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
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from gab_arrete");
        jdbc.update("delete from gab_appro");
        jdbc.update("delete from gab");
        jdbc.update("delete from bin_carte");
        jdbc.update("delete from compense_traitement");
        jdbc.update("delete from rapprochement_ecart");
        jdbc.update("delete from batch_execution");
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_LIS"})
    void aucunFichierLis() throws Exception {
        mvc.perform(get("/api/alertes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("LIS_AUCUN"))
                .andExpect(jsonPath("$[0].niveau").value("INFO"))
                .andExpect(jsonPath("$[0].lien").value("/ingestion"));
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_EXPLORATION_LIS"})
    void alertesFiltreesSurLesMenus() throws Exception {
        mvc.perform(get("/api/alertes")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_LIS", "MENU_CHARGEBACK_ACQUISITION"})
    void chargebackProcheDeLEcheanceEtLisEnRetard() throws Exception {
        // Chargeback reçu il y a 40 jours : échéance (45 jours) dans 5 jours.
        LocalDate journee = LocalDate.now().minusDays(40);
        byte[] f = FichierLisSynthetique.creer(JJMMAA.format(journee), "001", List.of(
                new Operation("17", "1", "4000000000000001", 75000, "74" + "0".repeat(19) + "01", "000000000001",
                        "99990001", JJMMAA.format(journee.minusDays(3)), "0090", null, "00008800", "03000343")));
        var r = lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_SW_" + journee.getDayOfYear() + "_01",
                TypeLis.SW, f)), "test");
        assertThat(r.integre()).as(r.toString()).isTrue();

        mvc.perform(get("/api/alertes"))
                .andExpect(jsonPath("$[0].code").value("CHARGEBACK_RECU_ECHEANCE"))
                .andExpect(jsonPath("$[0].niveau").value("CRITIQUE"))
                .andExpect(jsonPath("$[0].nombre").value(1))
                .andExpect(jsonPath("$[0].montant").value(75000))
                .andExpect(jsonPath("$[*].code", hasItem("LIS_EN_RETARD")))
                .andExpect(jsonPath("$[*].code", not(hasItem("CHARGEBACK_EMIS_ACQUIS"))));
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEMENT_EJOURNAL", "MENU_GAB_VALIDATION", "MENU_COMPENSE_VALIDATION"})
    void gabSansJournalEtSaisiesAValider() throws Exception {
        jdbc.update("insert into gab (terminal_id, libelle, marque, profil_code) values ('99990001', 'GAB test', 'NCR', '001')");
        jdbc.update("""
                insert into compense_traitement (reference, journee, parametres, nb_transactions, montant, commission, taf, statut, cree_par)
                values ('9999999', current_date, '{}', 2, 30000, 300, 51, 'SAISI', 'agent')""");
        mvc.perform(get("/api/alertes"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.code == 'GAB_SANS_JOURNAL')].nombre").value(1))
                .andExpect(jsonPath("$[?(@.code == 'COMPENSE_A_VALIDER')].montant").value(30000));
    }
}
