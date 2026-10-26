package com.rapmonet.securite.internal;

import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Politique de mot de passe paramétrable, expiration, historique et double authentification. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteRenforceeIntegrationTest {

    private static final String POLITIQUE_DEFAUT = """
            {"longueurMin":10,"majuscule":true,"minuscule":true,"chiffre":true,"special":false,
             "expirationJours":90,"historique":5,"echecsMax":3,"deuxFacteursObligatoire":false}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encodeur;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void reinitialiser() throws Exception {
        jdbc.update("delete from securite_utilisateur where login <> 'admin'");
        jdbc.update("""
                update securite_utilisateur set mot_de_passe = ?, doit_changer_mdp = false, actif = true, verrouille = false,
                    nb_echecs = 0, mdp_modifie_le = now(), totp_secret = null, totp_dernier_pas = null where login = 'admin'""",
                encodeur.encode("Admin2026"));
        jdbc.update("delete from securite_mdp_historique");
        politique(POLITIQUE_DEFAUT);
    }

    @AfterEach
    void restaurer() throws Exception {
        politique(POLITIQUE_DEFAUT);
    }

    private void politique(String corps) throws Exception {
        // L'administrateur de test n'a pas de double authentification : elle est levée avant sa connexion.
        jdbc.update("update parametre_application set valeur = 'false' where cle = 'securite.deux_facteurs.obligatoire'");
        MockHttpSession admin = connecter("admin", "Admin2026");
        mvc.perform(put("/api/securite/politique").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(corps)).andExpect(status().isOk());
    }

    private ResultActions connexion(String login, String motDePasse, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/auth/connexion").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"motDePasse\":\"%s\"}".formatted(login, motDePasse)));
    }

    private MockHttpSession connecter(String login, String motDePasse) throws Exception {
        MockHttpSession session = new MockHttpSession();
        connexion(login, motDePasse, session).andExpect(status().isOk());
        return session;
    }

    private ResultActions code(MockHttpSession session, String code) throws Exception {
        return mvc.perform(post("/api/auth/code").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private void creerAgent(String login, String motDePasse) {
        jdbc.update("""
                insert into securite_utilisateur (login, nom, profil_code, mot_de_passe, doit_changer_mdp)
                values (?, ?, 'CONSULTATION', ?, false)""", login, "Agent " + login, encodeur.encode(motDePasse));
    }

    private ResultActions changer(MockHttpSession session, String actuel, String nouveau) throws Exception {
        return mvc.perform(post("/api/auth/mot-de-passe").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actuel\":\"%s\",\"nouveau\":\"%s\"}".formatted(actuel, nouveau)));
    }

    /** Active la double authentification ; renvoie le secret. */
    private byte[] activer(MockHttpSession session) throws Exception {
        String reponse = mvc.perform(post("/api/auth/deux-facteurs/enrolement").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uri", containsString("otpauth://totp/RAPMONET")))
                .andExpect(jsonPath("$.qrSvg", containsString("<svg")))
                .andReturn().getResponse().getContentAsString();
        byte[] secret = Totp.depuisBase32(json.readTree(reponse).get("secret").asString());
        mvc.perform(post("/api/auth/deux-facteurs/activation").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000x\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/auth/deux-facteurs/activation").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.code(secret, Totp.pas(Instant.now())) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deuxFacteurs").value(true));
        return secret;
    }

    @Test
    void compositionEtHistoriqueDesMotsDePasse() throws Exception {
        creerAgent("agent1", "Depart2026a");
        MockHttpSession s = connecter("agent1", "Depart2026a");
        changer(s, "Depart2026a", "sansmajuscule26").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("une majuscule")));
        changer(s, "Depart2026a", "Agent1Secret26").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("identifiant")));
        changer(s, "Depart2026a", "Deuxieme2026").andExpect(status().isOk());
        changer(s, "Deuxieme2026", "Troisieme2026").andExpect(status().isOk());
        // Retour à un ancien mot de passe : refusé tant qu'il est dans l'historique.
        changer(s, "Troisieme2026", "Depart2026a").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("déjà été utilisé")));

        politique(POLITIQUE_DEFAUT.replace("\"special\":false", "\"special\":true"));
        changer(s, "Troisieme2026", "Quatrieme2026").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("caractère spécial")));
        changer(s, "Troisieme2026", "Quatrieme2026!").andExpect(status().isOk());

        mvc.perform(get("/api/auth/politique-mot-de-passe").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$[4].libelle", containsString("spécial")));
    }

    @Test
    void motDePasseExpireImposeLeChangement() throws Exception {
        creerAgent("agent2", "Depart2026a");
        jdbc.update("update securite_utilisateur set mdp_modifie_le = now() - interval '91 days' where login = 'agent2'");
        MockHttpSession s = new MockHttpSession();
        connexion("agent2", "Depart2026a", s).andExpect(status().isOk())
                .andExpect(jsonPath("$.doitChangerMotDePasse").value(true));
        mvc.perform(get("/api/exploration/lis/transactions").session(s)).andExpect(status().isForbidden());
        changer(s, "Depart2026a", "Nouveau2026b").andExpect(status().isOk())
                .andExpect(jsonPath("$.doitChangerMotDePasse").value(false));
    }

    @Test
    void nombreDEchecsParametrable() throws Exception {
        politique(POLITIQUE_DEFAUT.replace("\"echecsMax\":3", "\"echecsMax\":5"));
        creerAgent("agent3", "Depart2026a");
        for (int i = 0; i < 4; i++) {
            connexion("agent3", "faux", new MockHttpSession()).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(AuthentificationService.MESSAGE_IDENTIFIANTS));
        }
        connexion("agent3", "faux", new MockHttpSession()).andExpect(jsonPath("$.detail", containsString("après 5 tentatives")));
    }

    @Test
    void doubleAuthentification() throws Exception {
        creerAgent("agent4", "Depart2026a");
        byte[] secret = activer(connecter("agent4", "Depart2026a"));

        // Le mot de passe seul n'ouvre pas la session.
        MockHttpSession s = new MockHttpSession();
        connexion("agent4", "Depart2026a", s).andExpect(status().isOk()).andExpect(jsonPath("$.codeRequis").value(true));
        mvc.perform(get("/api/auth/moi").session(s)).andExpect(status().isUnauthorized());
        code(s, "123456x").andExpect(status().isUnauthorized());
        String suivant = Totp.code(secret, Totp.pas(Instant.now()) + 1);
        code(s, suivant).andExpect(status().isOk()).andExpect(jsonPath("$.login").value("agent4"));
        mvc.perform(get("/api/exploration/lis/transactions").session(s)).andExpect(status().isOk());

        // Un code déjà utilisé est refusé (rejeu).
        MockHttpSession s2 = new MockHttpSession();
        connexion("agent4", "Depart2026a", s2).andExpect(jsonPath("$.codeRequis").value(true));
        code(s2, suivant).andExpect(status().isUnauthorized());
        // Sans mot de passe préalable, le code est refusé.
        code(new MockHttpSession(), suivant).andExpect(status().isUnauthorized());

        // Téléphone perdu : l'administrateur réinitialise.
        MockHttpSession admin = connecter("admin", "Admin2026");
        mvc.perform(post("/api/securite/utilisateurs/agent4/deux-facteurs/reinitialisation").session(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deuxFacteurs").value(false));
        connexion("agent4", "Depart2026a", new MockHttpSession()).andExpect(jsonPath("$.login").value("agent4"));
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'DEUX_FACTEURS_REINITIALISATION'",
                Integer.class)).isPositive();
    }

    @Test
    void doubleAuthentificationObligatoire() throws Exception {
        creerAgent("agent5", "Depart2026a");
        politique(POLITIQUE_DEFAUT.replace("\"deuxFacteursObligatoire\":false", "\"deuxFacteursObligatoire\":true"));
        MockHttpSession s = new MockHttpSession();
        connexion("agent5", "Depart2026a", s).andExpect(jsonPath("$.doitActiverDeuxFacteurs").value(true));
        mvc.perform(get("/api/exploration/lis/transactions").session(s)).andExpect(status().isForbidden());
        activer(s);
        mvc.perform(get("/api/exploration/lis/transactions").session(s)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/deux-facteurs/desactivation").session(s).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motDePasse\":\"Depart2026a\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("obligatoire")));
        // Un utilisateur sans double authentification (ici l'administrateur) doit l'activer pour continuer.
        MockHttpSession admin = new MockHttpSession();
        connexion("admin", "Admin2026", admin).andExpect(jsonPath("$.doitActiverDeuxFacteurs").value(true));
        mvc.perform(get("/api/securite/politique").session(admin)).andExpect(status().isForbidden());
    }

    @Test
    void politiqueReserveeEtControlee() throws Exception {
        creerAgent("agent6", "Depart2026a");
        MockHttpSession agent = connecter("agent6", "Depart2026a");
        mvc.perform(get("/api/securite/politique").session(agent)).andExpect(status().isForbidden());
        MockHttpSession admin = connecter("admin", "Admin2026");
        mvc.perform(get("/api/securite/politique").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.longueurMin").value(10));
        mvc.perform(put("/api/securite/politique").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(POLITIQUE_DEFAUT.replace("\"longueurMin\":10", "\"longueurMin\":4")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void codeTotpConformeALaRfc6238() {
        // Vecteur de test de la RFC 6238 (SHA-1, T = 59 s) : 94287082 sur 8 chiffres, donc 287082 sur 6.
        byte[] secret = "12345678901234567890".getBytes();
        assertThat(Totp.code(secret, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.depuisBase32(Totp.base32(secret))).isEqualTo(secret);
    }
}
