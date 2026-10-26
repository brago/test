package com.rapmonet.parametrage;

import com.rapmonet.ingestion.IntegrationJournaux;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conservation des données : durées minimales, aperçu, purge confirmée, données récentes et en cours conservées. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_CONSERVATION"})
class ConservationIntegrationTest {

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
        jdbc.update("delete from reclamation");
        jdbc.update("delete from purge_execution");
        jdbc.update("delete from batch_execution");
        jdbc.update("delete from parametre_application where cle like 'conservation.%.mois'");
    }

    private ResultActions envoyer(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r, String corps)
            throws Exception {
        return mvc.perform(r.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private void reclamation(String numero, String statut, String cloture) {
        jdbc.update("""
                insert into reclamation (numero, carte, client, canal, objet, description, statut, echeance, cloture_le)
                values (?, '411111******1111', 'Client', 'GUICHET', 'AUTRE', 'Essai', ?, current_date, cast(? as timestamptz))""",
                numero, statut, cloture);
    }

    @Test
    void apercuPuisPurgeConfirmee() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
        // Journal vieux de 11 ans ; réclamations : une close ancienne, une ouverte ancienne, une close récente.
        jdbc.update("update ej_fichier set date_fin = current_date - interval '11 years'");
        reclamation("REC-T-1", "FONDEE", "2010-01-01");
        reclamation("REC-T-2", "OUVERTE", null);
        jdbc.update("update reclamation set cree_le = '2010-01-01' where numero = 'REC-T-2'");
        reclamation("REC-T-3", "NON_FONDEE", "2025-12-01");
        jdbc.update("insert into audit_evenement (horodatage, login, action, objet) values ('2010-01-01', 'ancien', 'CONNEXION', 'ancien')");

        mvc.perform(get("/api/parametrage/conservation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.code == 'JOURNAUX_GAB')].aPurger").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.categories[?(@.code == 'RECLAMATIONS')].aPurger").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.categories[?(@.code == 'AUDIT')].aPurger").value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.planification.planifiee").value(false));

        // Durée sous le minimum : refusée ; au-dessus : enregistrée et historisée.
        envoyer(put("/api/parametrage/conservation/categories/JOURNAUX_GAB"), "{\"mois\":12,\"motif\":\"Essai minimum\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("10 an(s)")));
        envoyer(put("/api/parametrage/conservation/categories/TRAITEMENTS_AUTOMATIQUES"), "{\"mois\":6,\"motif\":\"Historique court\"}")
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select valeur from parametre_application where cle = 'conservation.traitements_automatiques.mois'",
                String.class)).isEqualTo("6");

        // Purge sans confirmation, puis confirmée.
        envoyer(post("/api/parametrage/conservation/purges"), "{\"motif\":\"Purge annuelle\",\"confirmation\":\"oui\"}")
                .andExpect(status().isUnprocessableContent());
        envoyer(post("/api/parametrage/conservation/purges"), "{\"motif\":\"Purge annuelle\",\"confirmation\":\"PURGER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TERMINE"))
                .andExpect(jsonPath("$.detail[?(@.code == 'JOURNAUX_GAB')].supprimes").value(org.hamcrest.Matchers.hasItem(1)));

        assertThat(jdbc.queryForObject("select count(*) from ej_fichier", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ej_operation", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select numero from reclamation order by numero", String.class))
                .containsExactly("REC-T-2", "REC-T-3");
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where login = 'ancien'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'PURGE'", Integer.class)).isPositive();
    }

    @Test
    void planificationMensuelle() throws Exception {
        envoyer(put("/api/parametrage/conservation/planification"), "{\"planifiee\":true,\"jour\":31,\"motif\":\"Essai jour\"}")
                .andExpect(status().isUnprocessableContent());
        envoyer(put("/api/parametrage/conservation/planification"), "{\"planifiee\":true,\"jour\":5,\"motif\":\"Purge mensuelle\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.planifiee").value(true));
        envoyer(put("/api/parametrage/conservation/planification"), "{\"planifiee\":false,\"jour\":1,\"motif\":\"Retour à la normale\"}")
                .andExpect(jsonPath("$.planifiee").value(false));
    }

    @Test
    @WithMockUser(username = "agent", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_APPLICATION"})
    void reserveAuMenuConservation() throws Exception {
        mvc.perform(get("/api/parametrage/conservation")).andExpect(status().isForbidden());
    }
}
