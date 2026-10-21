package com.rapmonet.reporting;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.AvisAcquisition;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lot 8 : indicateurs par mois et base trimestrielle, sur des LIS synthétiques et le journal NCR anonymisé. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(authorities = {"UTILISATEUR", "MENU_REPORTING_KPI", "MENU_REPORTING_REGLEMENTAIRE"})
class ReportingIntegrationTest {

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
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("delete from bin_carte");
        jdbc.update("delete from gab");
    }

    private static Operation retraitDeNosCartes(String carte, long montant, String date) {
        return new Operation("07", "1", carte, montant, "74" + "0".repeat(19) + montant % 100, "000000000001", "AUTRE001", date,
                null, null, "00008800", "49999900");
    }

    private void integrer() throws Exception {
        byte[] sw = FichierLisSynthetique.creer("100426", "001",
                List.of(retraitDeNosCartes("4999990000000001", 20000, "150326"),
                        retraitDeNosCartes("4999990000000002", 30000, "020426")));
        byte[] pr = FichierLisSynthetique.creer("100426", "001", List.of(),
                List.of(new AvisAcquisition("4000000000000001", 50000, "99990001", "6011", "200326", "GAB TEST"),
                        new AvisAcquisition("4000000000000002", 10000, "99990001", "6011", "050426", "GAB TEST"),
                        new AvisAcquisition("4000000000000003", 70000, "99990002", "6011", "060426", "GAB TEST 2"),
                        new AvisAcquisition("4000000000000004", 15000, "TPE00001", "5411", "070426", "SUPERMARCHE")));
        var r = lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_SW_26100_01", TypeLis.SW, sw),
                new IntegrationLis.FichierRecu("LIS_IN_TEST_PR_26100_01", TypeLis.PR, pr)), "test");
        assertThat(r.integre()).as(r.toString()).isTrue();
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
    }

    @Test
    void kpiParGabEtParMois() throws Exception {
        integrer();
        jdbc.update("insert into gab (terminal_id, libelle, marque, profil_code) values ('99990002', 'GAB du siège', 'NCR', '001')");
        mvc.perform(get("/api/reporting/kpi/indicateurs")).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "GAB_RETRAITS_CONFRERES").param("du", "2026-03").param("au", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mois", hasSize(2)))
                .andExpect(jsonPath("$.totalNombre").value(3))
                .andExpect(jsonPath("$.totalMontant").value(130000))
                // Trié par montant : le GAB 99990002 (70 000) puis 99990001 (60 000).
                .andExpect(jsonPath("$.lignes[0].entite").value("99990002"))
                .andExpect(jsonPath("$.lignes[0].libelle").value("GAB du siège"))
                .andExpect(jsonPath("$.lignes[1].libelle").value("GAB TEST"))
                .andExpect(jsonPath("$.lignes[1].montants[0]").value(50000))
                .andExpect(jsonPath("$.lignes[1].montants[1]").value(10000))
                .andExpect(jsonPath("$.totauxNombre[1]").value(2));
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "TPE_ACHATS_CONFRERES").param("du", "2026-04").param("au", "2026-04"))
                .andExpect(jsonPath("$.lignes[0].entite").value("TPE00001"))
                .andExpect(jsonPath("$.totalMontant").value(15000));
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "GAB_RETRAITS_JOURNAUX").param("du", "2026-06").param("au", "2026-06"))
                .andExpect(jsonPath("$.lignes[0].entite").value("99990001"))
                .andExpect(jsonPath("$.totalNombre").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "GAB_RETRAITS_CONFRERES").param("du", "2026-04").param("au", "2026-03"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/reporting/kpi/export").param("format", "xlsx").param("indicateur", "GAB_RETRAITS_CONFRERES")
                        .param("du", "2026-03").param("au", "2026-04").param("mesure", "NOMBRE"))
                .andExpect(status().isOk());
    }

    @Test
    void kpiCartesSelonLesBinDeLaBanque() throws Exception {
        integrer();
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "CARTE_RETRAITS_AUTRES_GAB").param("du", "2026-03").param("au", "2026-04"))
                .andExpect(jsonPath("$.lignes[0].entite").value("499999"))
                .andExpect(jsonPath("$.totalMontant").value(50000))
                .andExpect(jsonPath("$.lignes[0].libelle").doesNotExist())
                .andExpect(jsonPath("$.avertissement", containsString("Aucun BIN")));
        jdbc.update("insert into bin_carte (bin, libelle, type_carte) values ('400000', 'Carte de test', 'DEBIT')");
        // Une fois les BIN déclarés, seules les cartes de la banque sont comptées (499999 n'en fait pas partie).
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "CARTE_RETRAITS_AUTRES_GAB").param("du", "2026-03").param("au", "2026-04"))
                .andExpect(jsonPath("$.totalNombre").value(0));
        // Retraits des cartes 400000 (déclarées de la banque) sur nos GAB, selon le journal anonymisé.
        mvc.perform(get("/api/reporting/kpi").param("indicateur", "CARTE_RETRAITS_NOS_GAB").param("du", "2026-06").param("au", "2026-06"))
                .andExpect(jsonPath("$.lignes[0].entite").value("400000"))
                .andExpect(jsonPath("$.lignes[0].libelle").value("Carte de test"));
    }

    @Test
    void baseTrimestrielleDesEtatsReglementaires() throws Exception {
        integrer();
        mvc.perform(get("/api/reporting/reglementaire/trimestre").param("annee", "2026").param("trimestre", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.du").value("2026-04-01"))
                .andExpect(jsonPath("$.au").value("2026-06-30"))
                .andExpect(jsonPath("$.lignes[?(@.rubrique =~ /Retraits de cartes.*nos GAB/)].montant").value(80000))
                .andExpect(jsonPath("$.lignes[?(@.rubrique =~ /Retraits de cartes.*nos GAB/)].entites").value(2))
                .andExpect(jsonPath("$.lignes[?(@.rubrique =~ /Retraits sur les GAB d.autres banques/)].reseau").value("GIM"))
                .andExpect(jsonPath("$.avertissement", containsString("BP4")));
        mvc.perform(get("/api/reporting/reglementaire/trimestre/export").param("format", "pdf").param("annee", "2026").param("trimestre", "2"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reporting/reglementaire/trimestre").param("annee", "2026").param("trimestre", "5"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_REPORTING_KPI"})
    void reglementaireReserveASonMenu() throws Exception {
        mvc.perform(get("/api/reporting/kpi/indicateurs")).andExpect(status().isOk());
        mvc.perform(get("/api/reporting/reglementaire/trimestre").param("annee", "2026").param("trimestre", "1"))
                .andExpect(status().isForbidden());
    }
}
