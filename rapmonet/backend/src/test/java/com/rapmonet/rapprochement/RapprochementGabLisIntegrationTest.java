package com.rapmonet.rapprochement;

import com.rapmonet.ingestion.FichierLisSynthetique;
import com.rapmonet.ingestion.FichierLisSynthetique.AvisAcquisition;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rapprochement GAB / LIS sur le journal NCR anonymisé (GAB 99990001) : trois retraits sont rendus « confrères »
 * (compte effacé), deux sont remontés dans un fichier LIS synthétique, les autres retraits confrères non ; deux avis n'ont pas de retrait.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "controleur", authorities = {"UTILISATEUR", "MENU_RAPPROCHEMENT_GAB_LIS"})
class RapprochementGabLisIntegrationTest {

    private static final DateTimeFormatter JJMMAA = DateTimeFormatter.ofPattern("ddMMyy");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

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
        jdbc.update("delete from rapprochement_ecart");
        jdbc.update("delete from bin_carte");
        jdbc.update("delete from rapprochement_regle_historique");
        reglesParDefaut();
    }

    private static final String REGLES_DEFAUT = """
            {"toleranceMinutes":120,"delaiLisJours":3,"memeCarte":true,"toleranceMontant":0,"reference":true,
             "champsReference":["AUTORISATION","BORDEREAU","RRN"],"fenetreReferenceJours":1}""";

    private void reglesParDefaut() {
        jdbc.update("update parametre_application set valeur = '120' where cle = 'rapprochement.gab_lis.tolerance_minutes'");
        jdbc.update("update parametre_application set valeur = '3' where cle = 'rapprochement.gab_lis.delai_lis_jours'");
        jdbc.update("update parametre_application set valeur = 'true' where cle in ('rapprochement.gab_lis.meme_carte',"
                + " 'rapprochement.gab_lis.reference_active')");
        jdbc.update("update parametre_application set valeur = '0' where cle = 'rapprochement.gab_lis.tolerance_montant'");
        jdbc.update("update parametre_application set valeur = 'AUTORISATION,BORDEREAU,RRN'"
                + " where cle = 'rapprochement.gab_lis.reference_champs'");
        jdbc.update("update parametre_application set valeur = '1' where cle = 'rapprochement.gab_lis.reference_fenetre_jours'");
    }

    private org.springframework.test.web.servlet.ResultActions modifierRegles(String regles, String motif) throws Exception {
        return mvc.perform(put("/api/rapprochement/regles/gab-lis").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"regles\":" + regles + ",\"motif\":\"" + motif + "\"}"));
    }

    private record Retrait(long id, String carte, long montant, LocalDateTime dateHeure, String reference) {
    }

    /** Intègre le journal, rend confrères les trois premiers retraits et les renvoie. */
    private List<Retrait> journal() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            assertThat(journaux.integrer("99990001_20260625.jrn", in.readAllBytes(), "test").integre()).isTrue();
        }
        List<Retrait> r = jdbc.query("""
                        select id, carte, montant, date_heure, reference from ej_operation
                        where type = 'RETRAIT' and statut = 'DISTRIBUE' order by date_heure limit 3""",
                (rs, n) -> new Retrait(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getTimestamp(4).toLocalDateTime(), rs.getString(5)));
        assertThat(r).hasSize(3);
        r.forEach(o -> jdbc.update("update ej_operation set compte = null where id = ?", o.id()));
        return r;
    }

    /** Numéro complet de test : 6 premiers et 4 derniers chiffres de la carte masquée du ticket. */
    private static String carte(Retrait r) {
        String c = r.carte().replaceAll("\\D", "");
        return c.substring(0, 6) + "000000" + c.substring(c.length() - 4);
    }

    private void lis(LocalDate journee, TypeLis type, List<AvisAcquisition> avis) {
        byte[] contenu = FichierLisSynthetique.creer(JJMMAA.format(journee), "001", List.of(), avis);
        var r = lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_" + type + "_" + journee.getDayOfYear() + "_01",
                type, contenu)), "test");
        assertThat(r.integre()).as(r.toString()).isTrue();
    }

    private void scenario(int joursApres) throws Exception {
        scenario(joursApres, TypeLis.PR);
    }

    private void scenario(int joursApres, TypeLis type) throws Exception {
        List<Retrait> r = journal();
        LocalDate jour = r.get(0).dateHeure().toLocalDate();
        Retrait a = r.get(0), b = r.get(1);
        lis(jour.plusDays(joursApres), type, List.of(
                // Même heure à 3 minutes près.
                AvisAcquisition.retrait(carte(a), a.montant(), "99990001", JJMMAA.format(jour),
                        HHMMSS.format(a.dateHeure().plusMinutes(3)), "A1B2C3"),
                // Heure très différente, mais code d'autorisation égal à la référence du ticket.
                AvisAcquisition.retrait(carte(b), b.montant(), "99990001", JJMMAA.format(b.dateHeure()),
                        HHMMSS.format(b.dateHeure().plusHours(5)), b.reference()),
                // Avis sans retrait au journal (le journal du GAB couvre ce jour).
                AvisAcquisition.retrait("4111110000001111", 20000, "99990001", JJMMAA.format(jour), "101010", "ZZZ999"),
                // GAB sans journal intégré.
                AvisAcquisition.retrait("4111110000002222", 30000, "99990009", JJMMAA.format(jour), "111111", "YYY888")));
    }

    @Test
    void rapprochementEtEcarts() throws Exception {
        scenario(5);
        LocalDate jour = jdbc.queryForObject("select min(date_heure)::date from ej_operation", LocalDate.class);
        int distribues = jdbc.queryForObject(
                "select count(*) from ej_operation where type = 'RETRAIT' and statut = 'DISTRIBUE'", Integer.class);
        int confreres = jdbc.queryForObject("select count(*) from ej_operation where type = 'RETRAIT' and statut = 'DISTRIBUE'"
                + " and coalesce(compte, '') = ''", Integer.class);
        assertThat(confreres).isGreaterThanOrEqualTo(3);
        String du = jour.minusDays(1).toString(), au = jour.plusDays(1).toString();

        mvc.perform(get("/api/rapprochement/gab-lis").param("du", du).param("au", au).param("vue", "TOUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthese.retraitsConfreres").value(confreres))
                .andExpect(jsonPath("$.synthese.retraitsOnUs").value(distribues - confreres))
                .andExpect(jsonPath("$.synthese.avisLis").value(4))
                .andExpect(jsonPath("$.synthese.rapproches").value(2))
                .andExpect(jsonPath("$.synthese.nonRemontes").value(confreres - 2))
                .andExpect(jsonPath("$.synthese.avisSansRetrait").value(1))
                .andExpect(jsonPath("$.synthese.montantAvisSansRetrait").value(20000))
                .andExpect(jsonPath("$.synthese.journalAbsent").value(1))
                .andExpect(jsonPath("$.synthese.enAttente").value(0))
                .andExpect(jsonPath("$.synthese.ecartsATraiter").value(confreres - 2 + 1))
                .andExpect(jsonPath("$.lignes", hasSize(confreres + 2)));

        // Vue par défaut : écarts et non rapprochés seulement.
        String reponse = mvc.perform(get("/api/rapprochement/gab-lis").param("du", du).param("au", au))
                .andExpect(jsonPath("$.lignes", hasSize(confreres)))
                .andExpect(jsonPath("$.lignes[0].resultat").value("RETRAIT_NON_REMONTE"))
                .andExpect(jsonPath("$.lignes[0].traitement").value("A_TRAITER"))
                .andExpect(jsonPath("$.lignes[" + (confreres - 2) + "].resultat").value("AVIS_SANS_RETRAIT"))
                .andExpect(jsonPath("$.lignes[" + (confreres - 1) + "].resultat").value("JOURNAL_ABSENT"))
                .andExpect(jsonPath("$.lignes[" + (confreres - 1) + "].traitement").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String cle = com.jayway.jsonpath.JsonPath.read(reponse, "$.lignes[0].cle");

        // Justifier sans commentaire : refusé.
        mvc.perform(post("/api/rapprochement/gab-lis/ecarts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cle\":\"" + cle + "\",\"typeEcart\":\"RETRAIT_NON_REMONTE\",\"statut\":\"JUSTIFIE\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/rapprochement/gab-lis/ecarts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cle\":\"" + cle + "\",\"typeEcart\":\"RETRAIT_NON_REMONTE\",\"statut\":\"JUSTIFIE\","
                                + "\"commentaire\":\"Réclamation GIM envoyée\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", du).param("au", au))
                .andExpect(jsonPath("$.synthese.ecartsATraiter").value(confreres - 2))
                .andExpect(jsonPath("$.lignes[0].traitement").value("JUSTIFIE"))
                .andExpect(jsonPath("$.lignes[0].commentaire").value("Réclamation GIM envoyée"));
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'RAPPROCHEMENT_ECART'",
                Integer.class)).isPositive();

        // Rapprochés : écart horaire de 3 minutes pour le premier.
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", du).param("au", au).param("vue", "RAPPROCHES"))
                .andExpect(jsonPath("$.lignes", hasSize(2)))
                .andExpect(jsonPath("$.lignes[?(@.ecartMinutes == 3)]", hasSize(1)));

        mvc.perform(get("/api/rapprochement/gab-lis/export").param("format", "xlsx").param("du", du).param("au", au))
                .andExpect(status().isOk());
    }

    @Test
    void retraitRecentEnAttenteDesLis() throws Exception {
        // Dernière journée LIS = jour + 1 : le GIM a encore le temps de remonter le troisième retrait.
        scenario(1);
        LocalDate jour = jdbc.queryForObject("select min(date_heure)::date from ej_operation", LocalDate.class);
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", jour.toString()).param("au", jour.toString()))
                .andExpect(jsonPath("$.synthese.enAttente").value(jdbc.queryForObject("select count(*) from ej_operation"
                        + " where type = 'RETRAIT' and statut = 'DISTRIBUE' and coalesce(compte, '') = ''", Integer.class) - 2))
                .andExpect(jsonPath("$.synthese.nonRemontes").value(0));
    }

    @Test
    void banqueEnSwitchingAvisDansLeFichierSw() throws Exception {
        // Banque en switching avec le GIM : les TC 49 arrivent dans le fichier SW, le PR est vide.
        scenario(5, TypeLis.SW);
        LocalDate jour = jdbc.queryForObject("select min(date_heure)::date from ej_operation", LocalDate.class);
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", jour.toString()).param("au", jour.toString()))
                .andExpect(jsonPath("$.synthese.avisLis").value(4))
                .andExpect(jsonPath("$.synthese.rapproches").value(2))
                .andExpect(jsonPath("$.synthese.avisSansRetrait").value(1));
    }

    @Test
    @WithMockUser(username = "responsable", authorities = {"UTILISATEUR", "MENU_RAPPROCHEMENT_GAB_LIS", "MENU_RAPPROCHEMENT_REGLES"})
    void reglesParametrablesSimuleesPuisEnregistrees() throws Exception {
        List<Retrait> r = journal();
        LocalDate jour = r.get(0).dateHeure().toLocalDate();
        Retrait a = r.get(0), b = r.get(1), c = r.get(2);
        lis(jour.plusDays(5), TypeLis.SW, List.of(
                // Montant LIS supérieur de 500 XOF (frais ajoutés par le réseau).
                AvisAcquisition.retrait(carte(a), a.montant() + 500, "99990001", JJMMAA.format(jour),
                        HHMMSS.format(a.dateHeure().plusMinutes(2)), "A1B2C3"),
                // Rapproché seulement sur la référence (5 heures d'écart).
                AvisAcquisition.retrait(carte(b), b.montant(), "99990001", JJMMAA.format(b.dateHeure()),
                        HHMMSS.format(b.dateHeure().plusHours(5)), b.reference()),
                // Carte différente (numéro mal transmis), même heure et même montant.
                AvisAcquisition.retrait("5000000000009999", c.montant(), "99990001", JJMMAA.format(c.dateHeure()),
                        HHMMSS.format(c.dateHeure().plusMinutes(1)), "Q1W2E3")));
        String du = jour.minusDays(1).toString(), au = jour.plusDays(1).toString();
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", du).param("au", au).param("vue", "TOUS"))
                .andExpect(jsonPath("$.synthese.rapproches").value(1))
                .andExpect(jsonPath("$.synthese.regles.memeCarte").value(true));

        // Simulation : montant à 1 000 XOF près, carte non comparée mais tolérance réduite, référence désactivée.
        String proposees = REGLES_DEFAUT.replace("\"toleranceMontant\":0", "\"toleranceMontant\":1000")
                .replace("\"memeCarte\":true", "\"memeCarte\":false").replace("\"toleranceMinutes\":120", "\"toleranceMinutes\":10")
                .replace("\"reference\":true", "\"reference\":false");
        mvc.perform(post("/api/rapprochement/regles/gab-lis/simulation").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regles\":" + proposees + ",\"du\":\"" + du + "\",\"au\":\"" + au + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actuelle.rapproches").value(1))
                .andExpect(jsonPath("$.proposee.rapproches").value(2))
                .andExpect(jsonPath("$.nbChangements").isNumber());
        // La simulation n'enregistre rien.
        mvc.perform(get("/api/rapprochement/regles/gab-lis")).andExpect(jsonPath("$.toleranceMontant").value(0));

        // Sans carte ni référence, la tolérance horaire est plafonnée.
        modifierRegles(proposees.replace("\"toleranceMinutes\":10", "\"toleranceMinutes\":60"), "Essai")
                .andExpect(status().isUnprocessableContent());
        modifierRegles(proposees, "").andExpect(status().isUnprocessableContent());
        modifierRegles(proposees, "Frais réseau ajoutés par le GIM").andExpect(status().isOk())
                .andExpect(jsonPath("$.toleranceMontant").value(1000));
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", du).param("au", au).param("vue", "RAPPROCHES"))
                .andExpect(jsonPath("$.synthese.rapproches").value(2))
                .andExpect(jsonPath("$.lignes[?(@.montantLis == " + (a.montant() + 500) + ")]", hasSize(1)));
        mvc.perform(get("/api/rapprochement/regles/gab-lis/historique"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].motif").value("Frais réseau ajoutés par le GIM"));
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'RAPPROCHEMENT_REGLES'",
                Integer.class)).isPositive();
    }

    @Test
    void reglesModifiablesSeulementAvecLeMenuRegles() throws Exception {
        mvc.perform(get("/api/rapprochement/regles/gab-lis")).andExpect(status().isOk());
        modifierRegles(REGLES_DEFAUT, "Essai sans droit").andExpect(status().isForbidden());
        mvc.perform(get("/api/rapprochement/regles/gab-lis/historique")).andExpect(status().isForbidden());
    }

    @Test
    void periodeObligatoire() throws Exception {
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", "2026-06-30").param("au", "2026-06-01"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser(authorities = {"UTILISATEUR", "MENU_GAB"})
    void reserveAuMenuRapprochement() throws Exception {
        mvc.perform(get("/api/rapprochement/gab-lis").param("du", "2026-06-01").param("au", "2026-06-30"))
                .andExpect(status().isForbidden());
    }
}
