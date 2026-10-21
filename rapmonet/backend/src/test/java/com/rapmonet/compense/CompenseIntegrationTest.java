package com.rapmonet.compense;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.AvisAcquisition;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.TypeLis;
import com.rapmonet.securite.UtilisateurConnecte;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compense TPE : référentiel importé d'un classeur synthétique, achats TC 49 d'un fichier LIS synthétique
 * (GIM d'une autre banque, GIM on-us, VISA, Mastercard), calculs et écritures de la macro, validation à quatre yeux.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompenseIntegrationTest {

    private static final String JOURNEE = "2026-04-10";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IntegrationLis lis;

    @BeforeEach
    void preparer() {
        vider();
        jdbc.update("insert into securite_profil (code, libelle, description) values ('COMPENSE_TEST', 'Compense', 'test')");
        jdbc.update("insert into securite_profil_menu (profil_code, menu_code) values ('COMPENSE_TEST', 'COMPENSE'), "
                + "('COMPENSE_TEST', 'COMPENSE_TRAITEMENT'), ('COMPENSE_TEST', 'COMPENSE_COMMERCANTS')");
        jdbc.update("insert into securite_utilisateur (login, nom, profil_code, mot_de_passe, doit_changer_mdp) values "
                + "('cp.saisie', 'Saisie', 'COMPENSE_TEST', 'x', false), ('cp.valideur', 'Valideur', 'ADMIN', 'x', false)");
    }

    @AfterEach
    void vider() {
        jdbc.update("delete from compense_traitement");
        jdbc.update("delete from commercant");
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from chargeback_dossier");
        jdbc.update("update parametre_application set valeur = '' where cle like 'compense.compte.%'");
        jdbc.update("delete from securite_utilisateur where login like 'cp.%'");
        jdbc.update("delete from securite_profil_menu where profil_code = 'COMPENSE_TEST'");
        jdbc.update("delete from securite_profil where code = 'COMPENSE_TEST'");
    }

    private static RequestPostProcessor en(String login) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new UtilisateurConnecte(login, login, null, null, null, Set.of(), false), null, List.of()));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r, String corps) {
        return r.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corps);
    }

    /** Classeur synthétique au format de la feuille « N° COMPTES » de la macro. */
    private static byte[] classeurCommercants() throws Exception {
        try (XSSFWorkbook c = new XSSFWorkbook(); var sortie = new ByteArrayOutputStream()) {
            var s = c.createSheet("N° COMPTES");
            s.createRow(0).createCell(0).setCellValue("RÉFÉRENTIEL COMMERÇANTS — TRAITEMENT COMPENSÉ TPE");
            var e = s.createRow(1);
            String[] h = {"TERMINAL ID", "NOMS COMMERCANTS", "N° COMPTE", "TAUX GIM", "TAUX VISA", "TAUX MCI"};
            for (int i = 0; i < h.length; i++) {
                e.createCell(i).setCellValue(h[i]);
            }
            var r1 = s.createRow(2);
            r1.createCell(0).setCellValue("01000001");
            r1.createCell(1).setCellValue("COMMERCE FICTIF UN");
            r1.createCell(2).setCellValue("09990000101");
            r1.createCell(3).setCellValue(0.01);
            r1.createCell(4).setCellValue(0.0125);
            r1.createCell(5).setCellValue(0.02);
            var r2 = s.createRow(3);
            r2.createCell(0).setCellValue(1000002);           // numérique : complété à 8 chiffres
            r2.createCell(1).setCellValue("COMMERCE FICTIF DEUX");
            r2.createCell(2).setCellValue("09990000102");
            r2.createCell(3).setCellValue("1,5%");             // texte en pourcentage
            r2.createCell(4).setCellValue(2.5);               // > 1 : pourcentage
            r2.createCell(5).setCellValue(0.02);
            var r3 = s.createRow(4);
            r3.createCell(0).setCellValue("01000003");
            r3.createCell(1).setCellValue("SANS COMPTE");
            c.write(sortie);
            return sortie.toByteArray();
        }
    }

    private void integrerLis() {
        byte[] pr = FichierLisSynthetique.creer("100426", "001", List.of(), List.of(
                new AvisAcquisition("4000000000000001", 21750, "01000001", "5411", "090426", "COMMERCE UN", "3", 250),
                new AvisAcquisition("4999990000000002", 20000, "01000002", "5411", "090426", "COMMERCE DEUX", "3", 0),
                new AvisAcquisition("4000000000000003", 13950, "01000001", "5411", "090426", "COMMERCE UN", "1", 0),
                new AvisAcquisition("5000000000000004", 20301, "01000002", "5812", "090426", "COMMERCE DEUX", "2", 0),
                new AvisAcquisition("4000000000000005", 50000, "99990001", "6011", "090426", "GAB", "3", 250),
                new AvisAcquisition("4000000000000006", 10000, "09999999", "5411", "090426", "INCONNU", "3", 250)));
        var r = lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_PR_26100_01", TypeLis.PR, pr)), "test");
        assertThat(r.integre()).as(r.toString()).isTrue();
    }

    private void parametrerComptes() throws Exception {
        mvc.perform(json(put("/api/compense/parametres"), """
                        {"compteEmisOnus":"09990000001","compteAcqGim":"09990000002","compteAcqVisa":"09990000003",
                         "compteAcqMci":"09990000004","compteCompensationVisaMci":"09990000005",
                         "compteCompensationGim":"09990000006","compteCommission":"09990000007","compteTaf":"09990000008",
                         "tauxTaf":0.17,"codeOperation":"91000"}""").with(en("cp.saisie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteTaf").value("09990000008"));
    }

    @Test
    void importDuReferentielCommercants() throws Exception {
        mvc.perform(multipart("/api/compense/commercants/import")
                        .file(new MockMultipartFile("fichier", "compense.xlsx", "application/octet-stream", classeurCommercants()))
                        .with(csrf()).with(en("cp.saisie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes").value(3))
                .andExpect(jsonPath("$.crees").value(2))
                .andExpect(jsonPath("$.anomalies", hasSize(1)))
                .andExpect(jsonPath("$.anomalies[0]", containsString("Ligne 5")));
        mvc.perform(get("/api/compense/commercants").with(en("cp.saisie")))
                .andExpect(jsonPath("$[?(@.terminalId == '01000002')].tauxGim").value(0.015))
                .andExpect(jsonPath("$[?(@.terminalId == '01000002')].tauxVisa").value(0.025));
        // Ré-import identique : rien de modifié.
        mvc.perform(multipart("/api/compense/commercants/import")
                        .file(new MockMultipartFile("fichier", "compense.xlsx", "application/octet-stream", classeurCommercants()))
                        .with(csrf()).with(en("cp.saisie")))
                .andExpect(jsonPath("$.crees").value(0))
                .andExpect(jsonPath("$.modifies").value(0));
    }

    @Test
    void compenseDUneJourneeCommeLaMacro() throws Exception {
        mvc.perform(multipart("/api/compense/commercants/import")
                .file(new MockMultipartFile("fichier", "c.xlsx", "application/octet-stream", classeurCommercants()))
                .with(csrf()).with(en("cp.saisie"))).andExpect(status().isOk());
        integrerLis();

        // Comptes non renseignés et terminal inconnu : compense impossible.
        mvc.perform(get("/api/compense/traitements/apercu").param("journee", JOURNEE).with(en("cp.saisie")))
                .andExpect(jsonPath("$.lignes", hasSize(5)))   // le retrait 6011 n'est pas un achat
                .andExpect(jsonPath("$.anomalies", hasItem(containsString("Comptes généraux"))))
                .andExpect(jsonPath("$.anomalies", hasItem(containsString("Terminal 09999999"))));
        mvc.perform(json(post("/api/compense/traitements"), "{\"journee\":\"" + JOURNEE + "\"}").with(en("cp.saisie")))
                .andExpect(status().isUnprocessableContent());

        parametrerComptes();
        mvc.perform(json(put("/api/compense/commercants/09999999"), """
                        {"nom":"COMMERCE FICTIF TROIS","compte":"09990000103","tauxGim":1.5,"tauxVisa":0,"tauxMci":0,"actif":true}""")
                        .with(en("cp.saisie")))
                .andExpect(jsonPath("$.tauxGim").value(0.015));

        String r = mvc.perform(json(post("/api/compense/traitements"), "{\"journee\":\"" + JOURNEE + "\"}").with(en("cp.saisie")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nbTransactions").value(5))
                .andExpect(jsonPath("$.montant").value(21750 + 20000 + 13950 + 20301 + 10000))
                // Commissions HT : 186 + 256 + 149 + 347 + 128 ; TAF : 32 + 44 + 25 + 59 + 22
                .andExpect(jsonPath("$.commission").value(186 + 256 + 149 + 347 + 128))
                .andExpect(jsonPath("$.taf").value(32 + 44 + 25 + 59 + 22))
                .andExpect(jsonPath("$.totaux[?(@.categorie == 'VISA')].montant").value(13950))
                .andExpect(jsonPath("$.totaux[?(@.categorie == 'GIM ONUS')].compteDebit").value("09990000001"))
                .andExpect(jsonPath("$.totaux[?(@.categorie == 'GIM ACQ')].nombre").value(2))
                .andExpect(jsonPath("$.lignes[?(@.montant == 21750)].frais").value(218))
                .andExpect(jsonPath("$.lignes[?(@.montant == 21750)].compteDebit").value("09990000002"))
                .andExpect(jsonPath("$.ecritures[0].libelle").value("Paiement Transactions du 10 Avril Vendredi TPE COMMERCE FICTIF UN REF 123456"))
                .andExpect(jsonPath("$.ecritures[0].montant").value(-21750))
                .andExpect(jsonPath("$.ecritures[?(@.libelle =~ /FRAIS ACTIVITE.*/)].dateOperation", hasItem("2026-04-11")))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(r.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        // Les achats déjà compensés ne sont pas repris.
        mvc.perform(json(post("/api/compense/traitements"), "{\"journee\":\"" + JOURNEE + "\"}").with(en("cp.saisie")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("déjà compensés")));

        // Macro comptable : brouillon possible, définitive après validation par une autre personne.
        mvc.perform(get("/api/compense/traitements/" + id + "/excel").with(en("cp.saisie")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/compense/traitements/" + id + "/excel").param("brouillon", "true").with(en("cp.saisie")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/compense/traitements/" + id + "/validation").with(csrf()).with(en("cp.saisie")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/compense/traitements/" + id + "/validation").with(csrf()).with(en("cp.valideur")))
                .andExpect(jsonPath("$.statut").value("VALIDE"));
        byte[] xlsx = mvc.perform(get("/api/compense/traitements/" + id + "/excel").with(en("cp.saisie")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook c = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            assertThat(c.getSheet("PIECE GIM").getRow(1).getCell(0).getStringCellValue()).isEqualTo("CPTE A DBT");
            var macro = c.getSheet("MACRO");
            assertThat(macro.getRow(1).getCell(19).getStringCellValue()).isEqualTo("DATCPTA");
            // Pièce équilibrée : la somme des écritures est nulle.
            double total = 0;
            for (int i = 2; i <= macro.getLastRowNum(); i++) {
                total += macro.getRow(i).getCell(3).getNumericCellValue();
            }
            assertThat(total).isZero();
            assertThat(macro.getLastRowNum() - 1).isEqualTo(5 * 5 + 8);
            assertThat(macro.getRow(2).getCell(1).getStringCellValue()).isEqualTo("09/04/2026");
            assertThat(macro.getRow(2).getCell(6).getStringCellValue()).isEqualTo("91000");
        }

        // Annulation : les achats redeviennent compensables.
        mvc.perform(json(post("/api/compense/traitements/" + id + "/annulation"), "{\"motif\":\"Erreur de taux\"}").with(en("cp.valideur")))
                .andExpect(jsonPath("$.statut").value("ANNULE"));
        mvc.perform(get("/api/compense/traitements/apercu").param("journee", JOURNEE).with(en("cp.saisie")))
                .andExpect(jsonPath("$.lignes", hasSize(5)))
                .andExpect(jsonPath("$.dejaCompenses").value(0));
        mvc.perform(get("/api/compense/traitements/journees").with(en("cp.saisie")))
                .andExpect(jsonPath("$[0]").value(JOURNEE));
    }
}
