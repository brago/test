package com.rapmonet.reclamation;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.AvisAcquisition;
import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.TypeLis;
import com.jayway.jsonpath.JsonPath;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recherche porteur (journaux, LIS, rapprochement) et cycle de vie d'une réclamation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "conseiller", authorities = {"UTILISATEUR", "MENU_RECLAMATION_RECHERCHE", "MENU_RECLAMATION_SUIVI"})
class ReclamationsIntegrationTest {

    private static final DateTimeFormatter JJMMAA = DateTimeFormatter.ofPattern("ddMMyy");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IntegrationJournaux journaux;

    @Autowired
    IntegrationLis lis;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("delete from rapprochement_ecart");
        jdbc.update("delete from reclamation");
        jdbc.update("delete from bin_carte");
    }

    private ResultActions rechercher(String corps) throws Exception {
        return mvc.perform(post("/api/reclamations/recherche").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    @Test
    void rechercheParCarteEtStatutDeRapprochement() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
        // Retrait distribué d'une carte confrère (sans compte imprimé) et son avis LIS.
        record Retrait(long id, String carte, long montant, LocalDateTime dateHeure) {
        }
        Retrait r = jdbc.queryForObject("""
                        select id, carte, montant, date_heure from ej_operation where type = 'RETRAIT' and statut = 'DISTRIBUE'
                        order by date_heure limit 1""",
                (rs, n) -> new Retrait(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toLocalDateTime()));
        jdbc.update("update ej_operation set compte = null where id = ?", r.id());
        String chiffres = r.carte().replaceAll("\\D", "");
        String debut = chiffres.substring(0, 6), fin = chiffres.substring(6);
        byte[] contenu = FichierLisSynthetique.creer(JJMMAA.format(r.dateHeure().plusDays(1)), "001", List.of(), List.of(
                AvisAcquisition.retrait(debut + "000000" + fin, r.montant(), "99990001", JJMMAA.format(r.dateHeure()),
                        HHMMSS.format(r.dateHeure().plusMinutes(2)), "R12345")));
        assertThat(lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_SW_001_01", TypeLis.SW, contenu)), "test")
                .integre()).isTrue();
        int operationsCarte = jdbc.queryForObject("select count(*) from ej_operation where regexp_replace(carte, '\\D', '', 'g') = ?",
                Integer.class, chiffres);

        String jour = r.dateHeure().toLocalDate().toString();
        String reponse = rechercher("{\"carte\":\"" + debut + "******" + fin + "\",\"du\":\"" + jour + "\",\"au\":\"" + jour + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carteMasquee").value(debut + "******" + fin))
                .andExpect(jsonPath("$.exacte").value(false))
                .andExpect(jsonPath("$.operationsGab", hasSize(operationsCarte)))
                .andExpect(jsonPath("$.transactionsLis", hasSize(1)))
                .andExpect(jsonPath("$.transactionsLis[0].autorisation").value("R12345"))
                .andReturn().getResponse().getContentAsString();
        List<String> resultats = JsonPath.read(reponse, "$.operationsGab[?(@.operation.id == " + r.id() + ")].rapprochement.resultat");
        assertThat(resultats).containsExactly("Rapproché");

        // Par référence (code d'autorisation LIS).
        rechercher("{\"reference\":\"R12345\"}").andExpect(jsonPath("$.transactionsLis", hasSize(1)));
        // Saisie invalide.
        rechercher("{\"carte\":\"1234\"}").andExpect(status().isUnprocessableContent());
        rechercher("{\"carte\":\"4111111111111112\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("clé de contrôle")));
        rechercher("{}").andExpect(status().isUnprocessableContent());
        // La piste d'audit ne reçoit que la carte masquée.
        assertThat(jdbc.queryForObject("select objet from audit_evenement where action = 'RECHERCHE_PORTEUR' order by id desc limit 1",
                String.class)).isEqualTo("Référence R12345");
    }

    @Test
    void cycleDeVieDUneReclamation() throws Exception {
        String reponse = mvc.perform(post("/api/reclamations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"carte":"4111111111111111","client":"Client Test","canal":"GUICHET","objet":"RETRAIT_NON_DISTRIBUE",
                         "description":"Retrait de 50 000 débité sans billets","montantConteste":50000,
                         "operations":[{"type":"GAB","id":1,"date":"2026-06-25T10:00","libelle":"Retrait GAB 99990001","montant":50000}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero", containsString("REC-")))
                .andExpect(jsonPath("$.carte").value("411111******1111"))
                .andExpect(jsonPath("$.statut").value("OUVERTE"))
                .andExpect(jsonPath("$.operations", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(reponse, "$.id");
        // Le numéro complet n'est conservé nulle part.
        assertThat(jdbc.queryForObject("select count(*) from reclamation where carte like '%1111111111%'", Integer.class)).isZero();

        String url = "/api/reclamations/" + id + "/actions";
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"PRISE_EN_CHARGE\"}"))
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"FONDEE\",\"commentaire\":\"court\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FONDEE\",\"commentaire\":\"Billets non pris, remboursement de 50 000 XOF\"}"))
                .andExpect(jsonPath("$.statut").value("FONDEE"))
                .andExpect(jsonPath("$.suivi", hasSize(3)))
                .andExpect(jsonPath("$.suivi[2].action").value("CLOTURE"));
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REOUVERTURE\",\"commentaire\":\"Le client conteste le montant\"}"))
                .andExpect(jsonPath("$.statut").value("EN_COURS"));

        // En retard : alerte de l'accueil.
        jdbc.update("update reclamation set echeance = current_date - 1 where id = ?", id);
        mvc.perform(get("/api/reclamations").param("statut", "A_TRAITER"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].enRetard").value(true));
        mvc.perform(get("/api/alertes")).andExpect(jsonPath("$[*].code", hasItem("RECLAMATIONS_EN_RETARD")));
        // Recherche porteur : la réclamation apparaît pour la carte.
        rechercher("{\"carte\":\"411111******1111\"}").andExpect(jsonPath("$.reclamations", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "agent", authorities = {"UTILISATEUR", "MENU_RECLAMATION_RECHERCHE"})
    void registreReserveAuMenuSuivi() throws Exception {
        mvc.perform(get("/api/reclamations")).andExpect(status().isForbidden());
        rechercher("{\"reference\":\"X\"}").andExpect(status().isOk());
    }
}
