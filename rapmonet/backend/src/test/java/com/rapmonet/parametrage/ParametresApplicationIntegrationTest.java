package com.rapmonet.parametrage;

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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paramètres de l'application : contrôles de valeur, motif, historique, paramètres gérés ailleurs, droits. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_APPLICATION"})
class ParametresApplicationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions modifier(String cle, String valeur, String motif) throws Exception {
        return mvc.perform(put("/api/parametrage/application/" + cle).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valeur\":\"" + valeur + "\",\"motif\":\"" + motif + "\"}"));
    }

    @Test
    void listeAvecParametresModifiablesEtGeresAilleurs() throws Exception {
        mvc.perform(get("/api/parametrage/application"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cle").value("application.nom"))
                .andExpect(jsonPath("$[?(@.cle == 'ejournal.ncr.format_date')].exemple").value(org.hamcrest.Matchers.hasItem(containsString("26/05/22"))))
                .andExpect(jsonPath("$[?(@.cle == 'securite.mdp.longueur_min')].modifiable").value(false))
                .andExpect(jsonPath("$[?(@.cle == 'securite.mdp.longueur_min')].lienEcran").value("/securite/politique"));
        modifier("securite.mdp.longueur_min", "8", "Essai contournement")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("Politique de sécurité")));
    }

    @Test
    void valeursControleesPuisHistorisees() throws Exception {
        modifier("gab.activite.taux_refus_alerte", "150", "Essai borne").andExpect(status().isUnprocessableContent());
        modifier("gab.activite.plage_debut", "7h", "Essai format").andExpect(status().isUnprocessableContent());
        modifier("gab.activite.plage_debut", "22:00", "Début après la fin").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("précéder")));
        modifier("ejournal.coupures", "10000,5000", "Essai liste").andExpect(status().isUnprocessableContent());
        modifier("ejournal.ncr.format_date", "HH:mm", "Essai format date").andExpect(status().isUnprocessableContent());
        modifier("application.devise", "xof1", "Essai devise").andExpect(status().isUnprocessableContent());
        modifier("chargeback.delai_jours", "60", "").andExpect(status().isUnprocessableContent());

        modifier("ejournal.coupures", " 10000 ; 5000 ; 2000 ; 1000 ", "Nouveaux billets de 1 000")
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeur").value("10000,5000,2000,1000"));
        modifier("ejournal.ncr.format_date", "dd/MM/yy", "Nouveau logiciel GAB")
                .andExpect(status().isOk()).andExpect(jsonPath("$.exemple", containsString("22/05/26")));
        modifier("ejournal.ncr.format_date", "dd/MM/yy", "Encore").andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("select valeur from parametre_application where cle = 'ejournal.coupures'", String.class))
                .isEqualTo("10000,5000,2000,1000");

        mvc.perform(get("/api/parametrage/application/historique"))
                .andExpect(jsonPath("$[0].cle").value("ejournal.ncr.format_date"))
                .andExpect(jsonPath("$[0].avant").value("yy/MM/dd"))
                .andExpect(jsonPath("$[0].motif").value("Nouveau logiciel GAB"))
                .andExpect(jsonPath("$[1].apres").value("10000,5000,2000,1000"));
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'PARAMETRAGE_APPLICATION'",
                Integer.class)).isPositive();
    }

    @Test
    @WithMockUser(username = "agent", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_LIS"})
    void reserveAuMenuParametresApplication() throws Exception {
        mvc.perform(get("/api/parametrage/application")).andExpect(status().isForbidden());
        modifier("chargeback.delai_jours", "60", "Essai sans droit").andExpect(status().isForbidden());
    }
}
