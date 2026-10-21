package com.rapmonet.chargeback;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.Operation;
import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.TypeLis;
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

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot 7 : chargebacks GIM reconstitués à partir de fichiers LIS synthétiques, avec le journal NCR anonymisé
 * (retrait de 75 000 XOF, carte se terminant par 6937, GAB 99990001, 25/06/2026).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEBACK_EMISSION", "MENU_CHARGEBACK_ACQUISITION"})
class ChargebackIntegrationTest {

    private static final String ARN_ACQUISITION = "74000000000000000000001";
    private static final String ARN_EMISSION = "74000000000000000000002";
    /** Carte d'une autre banque utilisée sur notre GAB (celle du journal anonymisé). */
    private static final String CARTE_ETRANGERE = "4000000000006937";
    /** Carte de la banque (BIN 499999 déclaré). */
    private static final String CARTE_BANQUE = "4999990000001234";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IntegrationLis lis;

    @Autowired
    IntegrationJournaux journaux;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from bin_carte");
    }

    private static Operation op(String tc, String usage, String carte, long montant, String arn, String terminal,
                                String motif, String message) {
        return new Operation(tc, usage, carte, montant, arn, "617600471941", terminal, "250626", motif, message,
                "00008800", "03000343");
    }

    private void integrerLis() {
        byte[] j1 = FichierLisSynthetique.creer("250626", "001", List.of(
                op("07", "1", CARTE_ETRANGERE, 75000, ARN_ACQUISITION, "99990001", null, null),
                op("07", "1", CARTE_BANQUE, 30000, ARN_EMISSION, "AUTRE001", null, null)));
        byte[] j2 = FichierLisSynthetique.creer("270626", "002", List.of(
                op("17", "1", CARTE_ETRANGERE, 75000, ARN_ACQUISITION, "99990001", "0090", null),
                op("17", "1", CARTE_BANQUE, 30000, ARN_EMISSION, "AUTRE001", "0090", null)));
        byte[] j3 = FichierLisSynthetique.creer("300626", "003", List.of(
                op("07", "2", CARTE_BANQUE, 30000, ARN_EMISSION, "AUTRE001", "0090", "CASH TAKEN")));
        for (var f : List.of(j1, j2, j3)) {
            var r = lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_SW_26176_01", TypeLis.SW, f)), "test");
            assertThat(r.integre()).as(r.toString()).isTrue();
        }
    }

    @Test
    void dossiersClassesSelonLesBinDeLaBanque() throws Exception {
        integrerLis();
        // Sans BIN déclaré, tous les chargebacks sont classés en acquisition (cartes d'autres banques).
        mvc.perform(get("/api/chargebacks/acquisition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binsParametres").value(false))
                .andExpect(jsonPath("$.page.total").value(2));

        jdbc.update("insert into bin_carte (bin, libelle, type_carte) values ('499999', 'Carte de test', 'DEBIT')");
        mvc.perform(get("/api/chargebacks/acquisition"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.elements[0].arn").value(ARN_ACQUISITION))
                .andExpect(jsonPath("$.page.elements[0].carte").value("400000******6937"))
                .andExpect(jsonPath("$.page.elements[0].journeeChargeback").value("2026-06-27"))
                .andExpect(jsonPath("$.page.elements[0].montant").value(75000))
                .andExpect(jsonPath("$.page.elements[0].motif").value("0090"))
                .andExpect(jsonPath("$.page.elements[0].libelleTc").value("Chargeback retrait"))
                .andExpect(jsonPath("$.page.elements[0].joursEcoules", greaterThan(45)))
                .andExpect(jsonPath("$.page.elements[0].etat").value("DELAI_ECOULE"));
        mvc.perform(get("/api/chargebacks/emission"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.elements[0].arn").value(ARN_EMISSION))
                .andExpect(jsonPath("$.page.elements[0].etat").value("REPRESENTE"));
        // Recherche par ARN ou RRN.
        mvc.perform(get("/api/chargebacks/emission").param("reference", "617600471941"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void cycleDeVieEtJournalDuGab() throws Exception {
        jdbc.update("insert into bin_carte (bin, libelle, type_carte) values ('499999', 'Carte de test', 'DEBIT')");
        integrerLis();
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
        long acq = jdbc.queryForObject("select id from chargeback_dossier where arn = ?", Long.class, ARN_ACQUISITION);
        long emi = jdbc.queryForObject("select id from chargeback_dossier where arn = ?", Long.class, ARN_EMISSION);

        mvc.perform(get("/api/chargebacks/acquisition/" + acq))
                .andExpect(jsonPath("$.cycle[0].etape").value("Première présentation"))
                .andExpect(jsonPath("$.cycle[0].situation", containsString("Compensée le 25/06/2026")))
                .andExpect(jsonPath("$.cycle[1].etape").value("Réception chargeback"))
                .andExpect(jsonPath("$.cycle[2].situation", containsString("délai dépassé")))
                .andExpect(jsonPath("$.operations.length()").value(2));
        mvc.perform(get("/api/chargebacks/emission/" + emi))
                .andExpect(jsonPath("$.cycle[1].etape").value("Couverture chargeback"))
                .andExpect(jsonPath("$.cycle[2].situation", containsString("CASH TAKEN")));
        // Un dossier d'émission n'est pas visible par l'URL d'acquisition.
        mvc.perform(get("/api/chargebacks/acquisition/" + emi)).andExpect(status().isNotFound());

        mvc.perform(get("/api/chargebacks/acquisition/" + acq + "/journal-gab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billetsPris").value(true))
                .andExpect(jsonPath("$.conclusion", containsString("Billets présentés et pris")))
                .andExpect(jsonPath("$.operations[0].montant").value(75000))
                .andExpect(jsonPath("$.operations[0].reference").value("471941"))
                .andExpect(jsonPath("$.session.texte", containsString("NOTES TAKEN")));
        mvc.perform(get("/api/chargebacks/emission/" + emi + "/journal-gab")).andExpect(status().isNotFound());
    }

    @Test
    void regularisationEtArchivage() throws Exception {
        integrerLis();
        long id = jdbc.queryForObject("select id from chargeback_dossier where arn = ?", Long.class, ARN_ACQUISITION);
        String url = "/api/chargebacks/acquisition/" + id + "/regularisation";
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"OUI\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"OUI\",\"commentaire\":\"Débit du compte GAB passé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularisation").value("OUI"));
        mvc.perform(get("/api/chargebacks/acquisition").param("regularisation", "NON"))
                .andExpect(jsonPath("$.page.total").value(1));

        mvc.perform(post("/api/chargebacks/acquisition/archivage").with(csrf()))
                .andExpect(jsonPath("$.archives").value(1));
        mvc.perform(get("/api/chargebacks/acquisition")).andExpect(jsonPath("$.page.total").value(1));
        mvc.perform(get("/api/chargebacks/acquisition").param("archives", "true")).andExpect(jsonPath("$.page.total").value(2));
        mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"NON\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/chargebacks/acquisition/export").param("format", "xlsx").param("archives", "true"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'CHARGEBACK_ARCHIVAGE'",
                Integer.class)).isPositive();
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_CHARGEBACK_EMISSION"})
    void chaqueSensASonMenu() throws Exception {
        mvc.perform(get("/api/chargebacks/emission")).andExpect(status().isOk());
        mvc.perform(get("/api/chargebacks/acquisition")).andExpect(status().isForbidden());
    }
}
