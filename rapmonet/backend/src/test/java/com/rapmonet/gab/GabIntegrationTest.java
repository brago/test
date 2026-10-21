package com.rapmonet.gab;

import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.securite.UtilisateurConnecte;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot 5 : paramétrage GAB, approvisionnements et arrêtés avec validation à quatre yeux, contrôles avec le
 * journal NCR anonymisé (GAB 99990001, passage du gabiste le 25/06/2026).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GabIntegrationTest {

    private static final String GAB = "99990001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IntegrationJournaux integration;

    @BeforeEach
    void preparer() {
        vider();
        jdbc.update("insert into securite_profil (code, libelle, description) values ('GABISTE_TEST', 'Gabiste', 'test')");
        jdbc.update("insert into securite_profil_menu (profil_code, menu_code) values "
                + "('GABISTE_TEST', 'GAB'), ('GABISTE_TEST', 'GAB_APPRO'), ('GABISTE_TEST', 'GAB_ARRETE')");
        for (String[] u : List.of(new String[]{"gab.saisie", "GABISTE_TEST"}, new String[]{"gab.valideur", "ADMIN"},
                new String[]{"gab.lecteur", "CONSULTATION"})) {
            jdbc.update("insert into securite_utilisateur (login, nom, profil_code, mot_de_passe, doit_changer_mdp) "
                    + "values (?, ?, ?, 'x', false)", u[0], u[0], u[1]);
        }
    }

    @AfterEach
    void vider() {
        jdbc.update("delete from gab_arrete");
        jdbc.update("delete from gab_appro");
        jdbc.update("delete from gab");
        jdbc.update("delete from gab_profil where code <> '001'");
        jdbc.update("delete from bin_carte");
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from securite_utilisateur where login like 'gab.%'");
        jdbc.update("delete from securite_profil_menu where profil_code = 'GABISTE_TEST'");
        jdbc.update("delete from securite_profil where code = 'GABISTE_TEST'");
    }

    private static RequestPostProcessor en(String login) {
        var u = new UtilisateurConnecte(login, login, null, null, null, Set.of(), false);
        return authentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r, String corps) {
        return r.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corps);
    }

    private void declarerGab() throws Exception {
        mvc.perform(json(put("/api/parametrage/gab/terminaux/" + GAB), """
                        {"libelle":"GAB de test","localisation":"Agence fictive","agence":"00001","marque":"ncr",
                         "profilCode":"001","compteCaisse":"09990000001","compteManquant":"09990000002",
                         "compteExcedent":"09990000003","actif":true}""").with(en("gab.valideur")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marque").value("NCR"));
    }

    private void integrerJournal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(integration.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
    }

    /** Crée un appro et renvoie son id. */
    private long appro(String date, String billets) throws Exception {
        String r = mvc.perform(json(post("/api/gab/appros"), """
                        {"terminal":"%s","dateAppro":"%s","gabiste":"Gabiste fictif","numeroBon":"B-1","billets":%s}"""
                        .formatted(GAB, date, billets)).with(en("gab.saisie")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(r.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    @Test
    void parametrageProfilsGabEtBin() throws Exception {
        // Un même type de billet ne peut pas avoir deux coupures : le journal compte les billets par type.
        mvc.perform(json(put("/api/parametrage/gab/profils/002"), """
                        {"libelle":"Incohérent","cassettes":[{"cassette":"A","coupure":10000,"type":1},
                         {"cassette":"B","coupure":5000,"type":1}]}""").with(en("gab.valideur")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("deux coupures")));
        mvc.perform(json(put("/api/parametrage/gab/profils/002"), """
                        {"libelle":"Deux coupures","cassettes":[{"cassette":"B","coupure":5000,"type":2},
                         {"cassette":"A","coupure":10000,"type":1}]}""").with(en("gab.valideur")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cassettes[0].cassette").value("A"));
        declarerGab();
        mvc.perform(get("/api/parametrage/gab/terminaux").with(en("gab.lecteur")))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(json(put("/api/parametrage/gab/terminaux/" + GAB), "{}").with(en("gab.lecteur")))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/parametrage/gab/bins/400000"), """
                        {"libelle":"Carte fictive","typeCarte":"DEBIT"}""").with(en("gab.valideur")))
                .andExpect(status().isOk());
        mvc.perform(json(put("/api/parametrage/gab/bins/12"), """
                        {"libelle":"Trop court","typeCarte":"DEBIT"}""").with(en("gab.valideur")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void approSaisiPuisValideParUneAutrePersonne() throws Exception {
        declarerGab();
        long id = appro("2026-06-24T09:00:00", """
                {"A":1000,"B":1000,"C":1000,"D":500}""");

        mvc.perform(get("/api/gab/appros/" + id).with(en("gab.saisie")))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("\\d{7}")))
                .andExpect(jsonPath("$.montant").value(1000 * 10000 + 1000 * 10000 + 1000 * 5000 + 500 * 2000))
                .andExpect(jsonPath("$.billetage[3].coupure").value(2000))
                .andExpect(jsonPath("$.statut").value("SAISI"))
                .andExpect(jsonPath("$.controleJournal.statut").value("ABSENT"));

        // Le gabiste n'a pas le droit de valider ; le valideur ne peut pas valider ce qu'il a lui-même modifié.
        mvc.perform(post("/api/gab/appros/" + id + "/validation").with(csrf()).with(en("gab.saisie")))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/gab/appros/" + id), """
                        {"terminal":"%s","dateAppro":"2026-06-24T09:00:00","gabiste":"Gabiste fictif",
                         "billets":{"A":1000,"B":1000,"C":1000,"D":400}}""".formatted(GAB)).with(en("gab.valideur")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/gab/appros/" + id + "/validation").with(csrf()).with(en("gab.valideur")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("quatre yeux")));
        mvc.perform(json(put("/api/gab/appros/" + id), """
                        {"terminal":"%s","dateAppro":"2026-06-24T09:00:00","gabiste":"Gabiste fictif",
                         "billets":{"A":1000,"B":1000,"C":1000,"D":500}}""".formatted(GAB)).with(en("gab.saisie")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/gab/appros/" + id + "/validation").with(csrf()).with(en("gab.valideur")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"))
                .andExpect(jsonPath("$.decidePar").value("gab.valideur"));
        mvc.perform(json(put("/api/gab/appros/" + id), "{}").with(en("gab.saisie")))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(get("/api/gab/appros").param("statut", "VALIDE").with(en("gab.saisie")))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/gab/appros").with(en("gab.lecteur"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/gab/appros/" + id + "/rapport").with(en("gab.saisie")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/gab/appros/export").param("format", "xlsx").with(en("gab.saisie")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'GAB_APPRO_VALIDATION' "
                + "and login = 'gab.valideur'", Integer.class)).isPositive();
    }

    @Test
    void approControleAvecLesBilletsAjoutesSelonLeJournal() throws Exception {
        declarerGab();
        integrerJournal();
        mvc.perform(get("/api/gab/appros/preparation").param("terminal", GAB).param("date", "2026-06-25").with(en("gab.saisie")))
                .andExpect(jsonPath("$.cassettes", hasSize(4)))
                .andExpect(jsonPath("$.passages", hasSize(1)))
                .andExpect(jsonPath("$.passages[0].ajoutes[0]").value(3118));

        // Billets de type 1 répartis entre les cassettes A et B (même coupure) : le contrôle se fait par type.
        long conforme = appro("2026-06-25T10:20:00", """
                {"A":1559,"B":1559,"C":1056,"D":1070}""");
        mvc.perform(get("/api/gab/appros/" + conforme).with(en("gab.saisie")))
                .andExpect(jsonPath("$.controleJournal.statut").value("CONCORDANT"))
                .andExpect(jsonPath("$.controleJournal.montantJournal").value(3118 * 10000 + 1056 * 5000 + 1070 * 2000));

        long ecart = appro("2026-06-25T10:30:00", """
                {"A":1559,"B":1500,"C":1056,"D":1070}""");
        mvc.perform(get("/api/gab/appros/" + ecart).with(en("gab.saisie")))
                .andExpect(jsonPath("$.controleJournal.statut").value("ECART"))
                .andExpect(jsonPath("$.controleJournal.message", containsString("journal 3118, saisie 3059")));
    }

    @Test
    void arreteAvecCompteursDuJournalInventaireEtEcarts() throws Exception {
        declarerGab();
        integrerJournal();
        long appro = appro("2026-06-24T09:00:00", """
                {"A":1000,"B":1000,"C":1000,"D":500}""");
        // Pas d'arrêté sur un appro non validé.
        mvc.perform(get("/api/gab/arretes/preparation").param("terminal", GAB).param("date", "2026-06-25T10:13:00")
                        .with(en("gab.saisie")))
                .andExpect(jsonPath("$.approsNonArretes", hasSize(0)));
        mvc.perform(post("/api/gab/appros/" + appro + "/validation").with(csrf()).with(en("gab.valideur")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/gab/arretes/preparation").param("terminal", GAB).param("date", "2026-06-25T10:13:00")
                        .with(en("gab.saisie")))
                .andExpect(jsonPath("$.approsNonArretes[0].id").value(appro))
                .andExpect(jsonPath("$.coupures[3]").value(0))
                .andExpect(jsonPath("$.passage.cassettes[0]").value(116))
                .andExpect(jsonPath("$.passage.rejets[0]").value(2))
                .andExpect(jsonPath("$.retraitsJournal").isNumber())
                // Appro du 24/06, arrêté du 25/06 : seul le journal du 25/06 est intégré.
                .andExpect(jsonPath("$.couverture.joursCouverts").value(1))
                .andExpect(jsonPath("$.couverture.joursPeriode").value(2));

        String saisie = """
                {"terminal":"%s","dateArrete":"2026-06-25T10:13:00","gabiste":"Gabiste fictif",
                 "journal":{"1":{"nombre":116,"rejet":2},"2":{"nombre":55,"rejet":1},"3":{"nombre":69,"rejet":1}},
                 "inventaire":{"A":{"nombre":60,"rejet":2},"B":{"nombre":56,"rejet":0},"C":{"nombre":55,"rejet":1},
                               "D":{"nombre":68,"rejet":1}},
                 "transactionsAttente":20000,"soldeComptable":1640000}""".formatted(GAB);
        String r = mvc.perform(json(post("/api/gab/arretes"), saisie).with(en("gab.saisie")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approId").value(appro))
                .andExpect(jsonPath("$.montantAppro").value(26_000_000))
                // (5) = 118 × 10 000 + 56 × 5 000 + 70 × 2 000
                .andExpect(jsonPath("$.montantJournal").value(1_600_000))
                // (6) = 118 × 10 000 + 56 × 5 000 + 69 × 2 000 : un billet de 2 000 manque
                .andExpect(jsonPath("$.montantPhysique").value(1_598_000))
                .andExpect(jsonPath("$.ecartPhysiqueJournal").value(-2000))
                // (6) + (7) − (8)
                .andExpect(jsonPath("$.ecartPhysiqueComptable").value(1_598_000 + 20_000 - 1_640_000))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(r.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

        // Un appro n'est arrêté qu'une fois ; il ne peut plus être annulé tant que l'arrêté existe.
        mvc.perform(json(post("/api/gab/arretes"), saisie.replace("\"terminal\"", "\"approId\":" + appro + ",\"terminal\""))
                        .with(en("gab.saisie")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("déjà un arrêté")));
        mvc.perform(json(post("/api/gab/appros/" + appro + "/annulation"), "{\"motif\":\"erreur\"}").with(en("gab.valideur")))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(post("/api/gab/arretes/" + id + "/validation").with(csrf()).with(en("gab.valideur")))
                .andExpect(jsonPath("$.statut").value("VALIDE"));
        mvc.perform(get("/api/gab/arretes/" + id + "/rapport").with(en("gab.saisie")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/gab/arretes/export").param("format", "pdf").with(en("gab.saisie")))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/gab/arretes/" + id + "/annulation"), "{\"motif\":\"\"}").with(en("gab.valideur")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(json(post("/api/gab/arretes/" + id + "/annulation"), "{\"motif\":\"Recomptage\"}").with(en("gab.valideur")))
                .andExpect(jsonPath("$.statut").value("ANNULE"));
        // Arrêté annulé : l'appro redevient à arrêter.
        mvc.perform(get("/api/gab/arretes/preparation").param("terminal", GAB).with(en("gab.saisie")))
                .andExpect(jsonPath("$.approsNonArretes[0].id").value(appro));
    }

    @Test
    void lesJournauxSontValorisesAvecLeProfilDuGab() throws Exception {
        mvc.perform(json(put("/api/parametrage/gab/profils/009"), """
                        {"libelle":"Autre chargement","cassettes":[{"cassette":"A","coupure":10000,"type":1},
                         {"cassette":"B","coupure":5000,"type":2},{"cassette":"C","coupure":1000,"type":3}]}""")
                        .with(en("gab.valideur")))
                .andExpect(status().isOk());
        mvc.perform(json(put("/api/parametrage/gab/terminaux/" + GAB), """
                        {"libelle":"GAB de test","marque":"NCR","profilCode":"009","actif":true}""").with(en("gab.valideur")))
                .andExpect(status().isOk());
        integrerJournal();
        // Type 3 valorisé à 1 000 au lieu de 2 000 : les retraits avec des billets de type 3 sont en écart.
        assertThat(jdbc.queryForObject("select count(*) from ej_operation where montant_billets <> montant", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForObject("select count(*) from ej_anomalie where message like '%billets présentés%'", Integer.class))
                .isPositive();
    }
}
