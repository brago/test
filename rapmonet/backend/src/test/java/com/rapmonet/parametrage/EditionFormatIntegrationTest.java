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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Modification du format LIS à l'écran : garde-fous, historique, prise en compte par la lecture. Annulé après chaque test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "parametreur", authorities = {"UTILISATEUR", "MENU_PARAMETRAGE_LIS"})
class EditionFormatIntegrationTest {

    private static final String CARTE = "LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FormatsFichiers formats;

    private ResultActions envoyer(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r, String corps)
            throws Exception {
        return mvc.perform(r.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private static String champ(String code, String libelle, int taille, String type, boolean specifique, boolean confidentiel) {
        return """
                {"code":"%s","libelle":"%s","description":null,"taille":%d,"pad":"ESPACE_DROITE","decimales":0,
                 "type":"%s","specifique":%b,"confidentiel":%b}""".formatted(code, libelle, taille, type, specifique, confidentiel);
    }

    private List<String> structure(String tc, String tcr) {
        return formats.trouver("LIS_GIM").orElseThrow().tc(tc).orElseThrow().tcr(tcr).orElseThrow().attributs().stream()
                .map(AttributFormat::code).collect(Collectors.toCollection(ArrayList::new));
    }

    @Test
    void champsProtegesEtConfidentiels() throws Exception {
        mvc.perform(get("/api/parametrage/formats/LIS_GIM/champs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + CARTE + "')].protege").value(true))
                .andExpect(jsonPath("$[?(@.code == '" + CARTE + "')].confidentiel").value(true));
        // Un numéro de carte reste masqué.
        envoyer(put("/api/parametrage/formats/LIS_GIM/champs/" + CARTE), "{\"champ\":"
                + champ(CARTE, "Numéro de carte", 19, "ALPHA", true, false) + ",\"motif\":\"Essai\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("confidentiel")));
        // Agrandir le champ ferait déborder les TCR (déjà remplis jusqu'à la position 256).
        envoyer(put("/api/parametrage/formats/LIS_GIM/champs/" + CARTE), "{\"champ\":"
                + champ(CARTE, "Numéro de carte", 25, "ALPHA", true, true) + ",\"motif\":\"Essai taille\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("dépasseraient")));
        // Motif obligatoire, puis libellé modifié et historisé.
        envoyer(put("/api/parametrage/formats/LIS_GIM/champs/" + CARTE), "{\"champ\":"
                + champ(CARTE, "Numéro de carte du porteur", 19, "ALPHA", true, true) + ",\"motif\":\"\"}")
                .andExpect(status().isUnprocessableContent());
        envoyer(put("/api/parametrage/formats/LIS_GIM/champs/" + CARTE), "{\"champ\":"
                + champ(CARTE, "Numéro de carte du porteur", 19, "ALPHA", true, true) + ",\"motif\":\"Libellé plus clair\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.libelle").value("Numéro de carte du porteur"));
        mvc.perform(get("/api/parametrage/formats/LIS_GIM/historique"))
                .andExpect(jsonPath("$[0].action").value("CHAMP_MODIFIE"))
                .andExpect(jsonPath("$[0].motif").value("Libellé plus clair"))
                .andExpect(jsonPath("$[0].apres", containsString("Numéro de carte du porteur")));
    }

    @Test
    void structureDUnTcrModifieeEtLueParLeFormat() throws Exception {
        List<String> champs = structure("49", "0");
        assertThat(champs).contains(CARTE);
        // Retirer un champ lu par les traitements : refusé.
        List<String> sansCarte = new ArrayList<>(champs);
        sansCarte.remove(CARTE);
        envoyer(put("/api/parametrage/formats/LIS_GIM/tcs/49/tcrs/0"), structureJson(sansCarte, "Essai"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString(CARTE)));
        // Doublon : refusé.
        List<String> doublon = new ArrayList<>(champs);
        doublon.add(CARTE);
        envoyer(put("/api/parametrage/formats/LIS_GIM/tcs/49/tcrs/0"), structureJson(doublon, "Essai"))
                .andExpect(status().isUnprocessableContent());

        // Le dernier champ non protégé est découpé : un nouveau champ de 2 caractères puis le reste.
        String dernier = champs.get(champs.size() - 1);
        int taille = jdbc.queryForObject("select taille from fmt_attribut where code = ?", Integer.class, dernier);
        assertThat(taille).isGreaterThan(2);
        assertThat(jdbc.queryForObject("select protege from fmt_attribut where code = ?", Boolean.class, dernier)).isFalse();
        envoyer(post("/api/parametrage/formats/LIS_GIM/champs"), "{\"champ\":"
                + champ("LIS_ESSAI_INDICATEUR", "Indicateur d'essai", 2, "ALPHA", true, false) + ",\"motif\":\"Bulletin GIM n° 12\"}")
                .andExpect(status().isCreated());
        envoyer(post("/api/parametrage/formats/LIS_GIM/champs"), "{\"champ\":"
                + champ("LIS_ESSAI_RESTE", "Réserve", taille - 2, "ALPHA", false, false) + ",\"motif\":\"Bulletin GIM n° 12\"}")
                .andExpect(status().isCreated());
        List<String> nouvelle = new ArrayList<>(champs.subList(0, champs.size() - 1));
        nouvelle.add("LIS_ESSAI_INDICATEUR");
        nouvelle.add("LIS_ESSAI_RESTE");
        // Sans le reste, puis avec un champ de trop : contrôle de la longueur.
        List<String> tropLong = new ArrayList<>(nouvelle);
        envoyer(post("/api/parametrage/formats/LIS_GIM/champs"), "{\"champ\":"
                + champ("LIS_ESSAI_EXTRA", "Extra", 1, "ALPHA", false, false) + ",\"motif\":\"Bulletin GIM n° 12\"}")
                .andExpect(status().isCreated());
        tropLong.add("LIS_ESSAI_EXTRA");
        envoyer(put("/api/parametrage/formats/LIS_GIM/tcs/49/tcrs/0"), structureJson(tropLong, "Essai"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("au plus 247")));
        envoyer(put("/api/parametrage/formats/LIS_GIM/tcs/49/tcrs/0"), structureJson(nouvelle, "Bulletin GIM n° 12"))
                .andExpect(status().isNoContent());

        AttributFormat ajoute = formats.trouver("LIS_GIM").orElseThrow().tc("49").orElseThrow().tcr("0").orElseThrow()
                .attributs().stream().filter(a -> a.code().equals("LIS_ESSAI_INDICATEUR")).findFirst().orElseThrow();
        assertThat(ajoute.positionFin()).isEqualTo(256 - (taille - 2));
        mvc.perform(get("/api/parametrage/formats/LIS_GIM/historique"))
                .andExpect(jsonPath("$[0].action").value("STRUCTURE_TCR"))
                .andExpect(jsonPath("$[0].objet").value("TC 49 / TCR 0"))
                .andExpect(jsonPath("$[0].apres", containsString("LIS_ESSAI_INDICATEUR")));
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'PARAMETRAGE_FORMAT'",
                Integer.class)).isPositive();
    }

    @Test
    void nouveauTc() throws Exception {
        String sousFichier = jdbc.queryForObject("select min(code) from fmt_sous_fichier where famille = 'LIS'", String.class);
        envoyer(post("/api/parametrage/formats/LIS_GIM/tcs"), """
                {"tc":{"code":"Z9","libelle":"Nouveau TC d'essai","description":null,
                       "tcrs":[{"code":"0","libelle":null},{"code":"1","libelle":"Complément"}],"sousFichiers":["%s"]},
                 "motif":"Annonce GIM"}""".formatted(sousFichier))
                .andExpect(status().isCreated());
        TcFormat tc = formats.trouver("LIS_GIM").orElseThrow().tc("Z9").orElseThrow();
        assertThat(tc.tcrs()).hasSize(2);
        assertThat(tc.tcrs().get(0).attributs()).singleElement()
                .satisfies(a -> assertThat(a.taille()).isEqualTo(247));
        assertThat(formats.trouver("LIS_GIM").orElseThrow().sousFichiers().stream()
                .filter(s -> s.code().equals(sousFichier)).findFirst().orElseThrow().tcAutorises()).contains("Z9");
        envoyer(post("/api/parametrage/formats/LIS_GIM/tcs"), """
                {"tc":{"code":"Z9","libelle":"Doublon","tcrs":[]},"motif":"Annonce GIM"}""")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser(username = "lecteur", authorities = {"UTILISATEUR"})
    void modificationReserveeAuParametrageLis() throws Exception {
        mvc.perform(get("/api/parametrage/formats/LIS_GIM/champs")).andExpect(status().isOk());
        envoyer(put("/api/parametrage/formats/LIS_GIM/tcs/49"), "{\"libelle\":\"X\",\"motif\":\"Essai droit\"}")
                .andExpect(status().isForbidden());
    }

    private static String structureJson(List<String> champs, String motif) {
        return "{\"champs\":[" + champs.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(","))
                + "],\"motif\":\"" + motif + "\"}";
    }
}
