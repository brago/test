package com.rapmonet.securite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Connexion, verrouillage, changement de mot de passe, habilitations et piste d'audit, de bout en bout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encodeur;

    @BeforeEach
    void reinitialiser() {
        jdbc.update("delete from securite_utilisateur where login <> 'admin'");
        jdbc.update("delete from securite_profil where code not in ('ADMIN', 'CONSULTATION')");
        jdbc.update("""
                update securite_utilisateur set mot_de_passe = ?, doit_changer_mdp = false, actif = true,
                    verrouille = false, nb_echecs = 0 where login = 'admin'""", encodeur.encode("Admin2026"));
        jdbc.update("delete from audit_evenement");
    }

    private ResultActions connexion(String login, String motDePasse, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/auth/connexion").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"motDePasse\":\"%s\"}".formatted(login, motDePasse)));
    }

    private MockHttpSession connecter(String login, String motDePasse) throws Exception {
        MockHttpSession session = new MockHttpSession();
        connexion(login, motDePasse, session).andExpect(status().isOk());
        return session;
    }

    private void creerUtilisateur(MockHttpSession admin, String login, String profil) throws Exception {
        mvc.perform(post("/api/securite/utilisateurs").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","nom":"Agent %s","agence":"Plateau","profilCode":"%s","motDePasseTemporaire":"Temporaire26"}"""
                                .formatted(login, login, profil)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doitChangerMdp").value(true));
    }

    @Test
    void lesApiExigentUneSession() throws Exception {
        mvc.perform(get("/api/exploration/lis/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/moi")).andExpect(status().isUnauthorized());
    }

    @Test
    void connexionSansJetonCsrfRefusee() throws Exception {
        mvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"admin\",\"motDePasse\":\"Admin2026\"}")).andExpect(status().isForbidden());
    }

    @Test
    void connexionEtDeconnexion() throws Exception {
        MockHttpSession session = connecter("admin", "Admin2026");
        mvc.perform(get("/api/auth/moi").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil").value("ADMIN"))
                .andExpect(jsonPath("$.menus", hasItem("SECURITE_AUDIT")));
        mvc.perform(post("/api/auth/deconnexion").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/moi").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void troisEchecsVerrouillentLeCompteJusquauDeverrouillage() throws Exception {
        MockHttpSession admin = connecter("admin", "Admin2026");
        creerUtilisateur(admin, "agent1", "CONSULTATION");

        connexion("agent1", "faux1", new MockHttpSession()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect."));
        connexion("agent1", "faux2", new MockHttpSession()).andExpect(status().isUnauthorized());
        connexion("agent1", "faux3", new MockHttpSession()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("verrouillé")));
        // Même le bon mot de passe est refusé.
        connexion("agent1", "Temporaire26", new MockHttpSession()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("verrouillé")));

        mvc.perform(post("/api/securite/utilisateurs/agent1/deverrouiller").session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verrouille").value(false));
        connexion("agent1", "Temporaire26", new MockHttpSession()).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'VERROUILLAGE' and login = 'agent1'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void premiereConnexionImposeLeChangementDeMotDePasse() throws Exception {
        MockHttpSession admin = connecter("admin", "Admin2026");
        creerUtilisateur(admin, "agent2", "CONSULTATION");

        MockHttpSession session = new MockHttpSession();
        connexion("agent2", "Temporaire26", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.doitChangerMotDePasse").value(true));
        mvc.perform(get("/api/exploration/lis/transactions").session(session)).andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/mot-de-passe").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actuel\":\"Temporaire26\",\"nouveau\":\"court\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/auth/mot-de-passe").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actuel\":\"Temporaire26\",\"nouveau\":\"Nouveau2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.doitChangerMotDePasse").value(false));
        mvc.perform(get("/api/exploration/lis/transactions").session(session)).andExpect(status().isOk());
    }

    @Test
    void lesDroitsSuiventLeProfilEtSAppliquentSansReconnexion() throws Exception {
        MockHttpSession admin = connecter("admin", "Admin2026");
        creerUtilisateur(admin, "agent3", "CONSULTATION");
        jdbc.update("update securite_utilisateur set doit_changer_mdp = false where login = 'agent3'");
        MockHttpSession agent = connecter("agent3", "Temporaire26");

        mvc.perform(get("/api/exploration/lis/transactions").session(agent)).andExpect(status().isOk());
        mvc.perform(get("/api/securite/utilisateurs").session(agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ingestion/lis").session(agent)).andExpect(status().isForbidden());

        // Un menu enfant sans son parent est refusé.
        mvc.perform(post("/api/securite/profils").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CHARGEUR\",\"libelle\":\"Chargeur\",\"menus\":[\"ACCUEIL\",\"CHARGEMENT_LIS\"]}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/securite/profils").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CHARGEUR\",\"libelle\":\"Chargeur\",\"menus\":[\"ACCUEIL\",\"CHARGEMENT\",\"CHARGEMENT_LIS\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/securite/utilisateurs/agent3").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Agent 3\",\"profilCode\":\"CHARGEUR\",\"actif\":true}"))
                .andExpect(status().isOk());

        // Le nouveau profil s'applique à la requête suivante.
        mvc.perform(get("/api/ingestion/lis").session(agent)).andExpect(status().isOk());
        mvc.perform(get("/api/exploration/lis/transactions").session(agent)).andExpect(status().isForbidden());

        // Un compte désactivé perd immédiatement sa session.
        mvc.perform(put("/api/securite/utilisateurs/agent3").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Agent 3\",\"profilCode\":\"CHARGEUR\",\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ingestion/lis").session(agent)).andExpect(status().isUnauthorized());
        connexion("agent3", "Temporaire26", new MockHttpSession()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("désactivé")));
    }

    @Test
    void leProfilAdminEstProtege() throws Exception {
        MockHttpSession admin = connecter("admin", "Admin2026");
        mvc.perform(put("/api/securite/profils/ADMIN").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ADMIN\",\"libelle\":\"X\",\"menus\":[\"ACCUEIL\"]}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/securite/utilisateurs/admin").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Administrateur\",\"profilCode\":\"ADMIN\",\"actif\":false}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void pisteDAuditConsultableEtExportable() throws Exception {
        connexion("admin", "mauvais", new MockHttpSession()).andExpect(status().isUnauthorized());
        MockHttpSession admin = connecter("admin", "Admin2026");

        mvc.perform(get("/api/securite/audit").param("login", "admin").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.elements[0].action").value("CONNEXION"))
                .andExpect(jsonPath("$.elements[0].succes").value(true))
                .andExpect(jsonPath("$.elements[1].succes").value(false));

        mvc.perform(get("/api/securite/audit/export").param("format", "pdf").session(admin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "application/pdf"));
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'EXPORT'", Integer.class))
                .isEqualTo(1);
    }
}
