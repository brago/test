package com.rapmonet.reporting;

import com.rapmonet.ingestion.IntegrationJournaux;
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

import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tableau de bord direction sur le journal anonymisé : indicateurs, comparaison, tendance, points d'attention. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "directeur", authorities = {"UTILISATEUR", "MENU_REPORTING_DIRECTION"})
class TableauBordDirectionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IntegrationJournaux journaux;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from gab_appro");
        jdbc.update("delete from gab_arrete");
        jdbc.update("delete from gab");
        jdbc.update("delete from reclamation");
    }

    @Test
    void indicateursDuMoisEtPointsDAttention() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
        jdbc.update("insert into gab (terminal_id, libelle, marque, profil_code) values ('99990001', 'GAB Siège', 'NCR', '001')");
        LocalDate jour = jdbc.queryForObject("select min(date_session) from ej_session", LocalDate.class);
        long retraits = jdbc.queryForObject("select count(*) from ej_operation where type = 'RETRAIT' and statut = 'DISTRIBUE'",
                Long.class);
        String mois = YearMonth.from(jour).toString();

        mvc.perform(get("/api/reporting/direction").param("mois", mois))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocs", hasSize(5)))
                .andExpect(jsonPath("$.blocs[0].titre").value("Activité des GAB"))
                .andExpect(jsonPath("$.blocs[0].indicateurs[0].valeur").value((double) retraits))
                .andExpect(jsonPath("$.blocs[0].indicateurs[0].precedent").value(0.0))
                .andExpect(jsonPath("$.tendance", hasSize(12)))
                .andExpect(jsonPath("$.tendance[11].nombre").value(retraits))
                .andExpect(jsonPath("$.dernierMoisAvecDonnees").value(mois))
                .andExpect(jsonPath("$.pointsAttention[*].texte", hasItem(containsString("sans journal"))));
        mvc.perform(get("/api/reporting/direction").param("mois", "2026-13")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/reporting/direction").param("mois", YearMonth.now().plusMonths(2).toString()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser(username = "agent", authorities = {"UTILISATEUR", "MENU_REPORTING_KPI"})
    void reserveAuMenuDirection() throws Exception {
        mvc.perform(get("/api/reporting/direction")).andExpect(status().isForbidden());
    }
}
