package com.rapmonet.gab;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activité des GAB sur le journal anonymisé : agrégats, signalements, détail, export, droits. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "exploitant", authorities = {"UTILISATEUR", "MENU_GAB_ACTIVITE"})
class ActiviteGabIntegrationTest {

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
        jdbc.update("delete from gab_appro");
        jdbc.update("delete from gab_arrete");
        jdbc.update("delete from gab");
        jdbc.update("update parametre_application set valeur = '25' where cle = 'gab.activite.taux_refus_alerte'");
        jdbc.update("update parametre_application set valeur = '10' where cle = 'gab.activite.tentatives_min'");
    }

    private LocalDate journal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
        return jdbc.queryForObject("select min(date_session) from ej_session", LocalDate.class);
    }

    @Test
    void activiteParGabEtSignalements() throws Exception {
        LocalDate jour = journal();
        int distribues = jdbc.queryForObject("select count(*) from ej_operation where type = 'RETRAIT' and statut = 'DISTRIBUE'"
                + " and cast(date_heure as date) = ?", Integer.class, jour);
        long montant = jdbc.queryForObject("select coalesce(sum(montant), 0) from ej_operation where type = 'RETRAIT'"
                + " and statut = 'DISTRIBUE' and cast(date_heure as date) = ?", Long.class, jour);
        int refuses = jdbc.queryForObject("select count(*) from ej_operation where type = 'RETRAIT' and statut = 'REFUSE'"
                + " and cast(date_heure as date) = ?", Integer.class, jour);
        int sessions = jdbc.queryForObject("select count(*) from ej_session where date_session = ?", Integer.class, jour);
        assertThat(distribues).isPositive();
        // GAB paramétré actif, sans journal la veille : signalé « jours sans journal ».
        jdbc.update("insert into gab (terminal_id, libelle, marque, profil_code) values ('99990001', 'GAB Siège', 'NCR', '001')");

        String du = jour.minusDays(1).toString(), au = jour.toString();
        mvc.perform(get("/api/gab/activite").param("du", du).param("au", au))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthese.retraits").value(distribues))
                .andExpect(jsonPath("$.synthese.montant").value(montant))
                .andExpect(jsonPath("$.synthese.refuses").value(refuses))
                .andExpect(jsonPath("$.gabs", hasSize(1)))
                .andExpect(jsonPath("$.gabs[0].libelle").value("GAB Siège"))
                .andExpect(jsonPath("$.gabs[0].sessions").value(sessions))
                .andExpect(jsonPath("$.gabs[0].joursCouverts").value(1))
                .andExpect(jsonPath("$.gabs[0].joursPeriode").value(2))
                .andExpect(jsonPath("$.gabs[0].signalements[*].code", hasItem("JOURS_SANS_JOURNAL")))
                .andExpect(jsonPath("$.parJour", hasSize(2)))
                .andExpect(jsonPath("$.parHeure", hasSize(24)));

        // Seuil de refus abaissé : le GAB est signalé pour ses refus.
        if (refuses > 0) {
            jdbc.update("update parametre_application set valeur = '1' where cle in ('gab.activite.taux_refus_alerte',"
                    + " 'gab.activite.tentatives_min')");
            mvc.perform(get("/api/gab/activite").param("du", au).param("au", au))
                    .andExpect(jsonPath("$.gabs[0].signalements[*].code", hasItem("TAUX_REFUS")))
                    .andExpect(jsonPath("$.refus[0].libelle").isString());
            mvc.perform(get("/api/alertes"))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/gab/activite/99990001").param("du", du).param("au", au))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jours", hasSize(1)))
                .andExpect(jsonPath("$.jours[0].jour.retraitsDistribues").value(distribues))
                .andExpect(jsonPath("$.joursSansJournal[0]").value(du));
        mvc.perform(get("/api/gab/activite/export").param("format", "xlsx").param("du", du).param("au", au))
                .andExpect(status().isOk());
    }

    @Test
    void periodeObligatoire() throws Exception {
        mvc.perform(get("/api/gab/activite").param("du", "2026-06-30").param("au", "2026-06-01"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("période")));
    }

    @Test
    @WithMockUser(username = "agent", authorities = {"UTILISATEUR", "MENU_GAB_APPRO"})
    void reserveAuMenuActivite() throws Exception {
        mvc.perform(get("/api/gab/activite").param("du", "2026-06-01").param("au", "2026-06-30")).andExpect(status().isForbidden());
    }
}
