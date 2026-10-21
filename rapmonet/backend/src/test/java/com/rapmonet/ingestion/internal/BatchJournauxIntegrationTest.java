package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.BatchJournaux;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Batch quotidien d'intégration des journaux GAB déposés dans un dossier (journal NCR anonymisé). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", authorities = {"UTILISATEUR", "MENU_CHARGEMENT_EJOURNAL"})
class BatchJournauxIntegrationTest {

    private static final String NOM = "99990001_20260625.jrn";

    @Autowired
    BatchJournauxService batch;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path racine;

    Path entree;
    Path archive;
    Path rejet;

    @BeforeEach
    void preparer() {
        jdbc.update("delete from ej_fichier");
        jdbc.update("delete from batch_execution");
        entree = racine.resolve("entree");
        archive = racine.resolve("archive");
        rejet = racine.resolve("rejet");
        batch.modifier(new BatchJournaux.Configuration(true, "04:00", entree.toString(), archive.toString(),
                rejet.toString()), "test");
    }

    @AfterEach
    void restaurer() {
        jdbc.update("delete from batch_execution");
        batch.modifier(new BatchJournaux.Configuration(true, "04:00", "donnees/ejournal/entree",
                "donnees/ejournal/archive", "donnees/ejournal/rejet"), "test");
    }

    private static byte[] journal() throws IOException {
        try (InputStream in = BatchJournauxIntegrationTest.class.getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            return in.readAllBytes();
        }
    }

    private static long compter(Path dossier) throws IOException {
        if (!Files.isDirectory(dossier)) {
            return 0;
        }
        try (Stream<Path> f = Files.walk(dossier)) {
            return f.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void integreEtArchiveLesJournauxDeposes() throws Exception {
        Files.createDirectories(entree.resolve("GAB01"));
        Files.write(entree.resolve("GAB01").resolve(NOM), journal());

        BatchJournaux.Execution ex = batch.executer("PLANIFIE", "BATCH");

        assertThat(ex.statut()).isEqualTo("TERMINE");
        assertThat(ex.nbFichiers()).isEqualTo(1);
        assertThat(ex.nbIntegres()).isEqualTo(1);
        assertThat(ex.nbOperations()).isEqualTo(31);
        assertThat(ex.fin()).isNotNull();
        assertThat(ex.detail().getFirst().fichier()).isEqualTo("GAB01/" + NOM);
        assertThat(ex.detail().getFirst().terminal()).isEqualTo("99990001");
        assertThat(compter(entree)).isZero();
        assertThat(archive.resolve("99990001").resolve("2026-06").resolve(NOM)).exists();
        assertThat(jdbc.queryForObject("select integre_par from ej_fichier", String.class)).isEqualTo("BATCH");
        assertThat(jdbc.queryForObject("select count(*) from audit_evenement where action = 'BATCH_EJOURNAL'"
                + " and login = 'BATCH' and horodatage > now() - interval '1 minute'", Integer.class)).isPositive();
    }

    @Test
    void journalDejaIntegreArchiveSansDoublon() throws Exception {
        Files.createDirectories(entree);
        Files.write(entree.resolve(NOM), journal());
        batch.executer("MANUEL", "admin");
        Files.write(entree.resolve(NOM), journal());

        BatchJournaux.Execution ex = batch.executer("MANUEL", "admin");

        assertThat(ex.statut()).isEqualTo("TERMINE");
        assertThat(ex.nbDejaIntegres()).isEqualTo(1);
        assertThat(ex.nbOperations()).isZero();
        // Deuxième copie archivée à côté de la première, sans l'écraser.
        assertThat(archive.resolve("99990001").resolve("2026-06").resolve(NOM + ".1")).exists();
        assertThat(jdbc.queryForObject("select count(*) from ej_fichier", Integer.class)).isEqualTo(1);
    }

    @Test
    void journalIllisibleMisEnRejetAvecLeMotif() throws Exception {
        Files.createDirectories(entree);
        Files.writeString(entree.resolve("inconnu.txt"), "ceci n'est pas un journal\n");

        BatchJournaux.Execution ex = batch.executer("MANUEL", "admin");

        assertThat(ex.statut()).isEqualTo("TERMINE_AVEC_REJETS");
        assertThat(ex.nbRejetes()).isEqualTo(1);
        assertThat(ex.detail().getFirst().message()).contains("Identifiant du GAB introuvable");
        Path dossier = rejet.resolve(LocalDate.now().toString());
        assertThat(dossier.resolve("inconnu.txt")).exists();
        assertThat(Files.readString(dossier.resolve("inconnu.txt.erreur.txt"))).contains("BLOQUANTE");
    }

    @Test
    void fichierEnCoursDeCopieLaissePourLePassageSuivant() throws Exception {
        Files.createDirectories(entree);
        Path f = Files.write(entree.resolve(NOM), journal());
        Files.write(entree.resolve(".cache"), new byte[]{1});
        Files.setLastModifiedTime(f, FileTime.from(Instant.now().plus(Duration.ofMinutes(5))));

        BatchJournaux.Execution ex = batch.executer("MANUEL", "admin");

        assertThat(ex.nbFichiers()).isZero();
        assertThat(ex.message()).startsWith("Aucun journal à intégrer");
        assertThat(f).exists();
    }

    @Test
    void uneSeuleExecutionALaFois() {
        jdbc.update("insert into batch_execution (traitement, declencheur, statut) values ('EJOURNAL', 'MANUEL', 'EN_COURS')");
        assertThatThrownBy(() -> batch.executer("PLANIFIE", "BATCH"))
                .isInstanceOf(RegleMetierException.class).hasMessageContaining("déjà en cours");
    }

    @Test
    void executionAbandonneeMarqueeInterrompue() {
        jdbc.update("insert into batch_execution (traitement, declencheur, statut, debut) "
                + "values ('EJOURNAL', 'PLANIFIE', 'EN_COURS', now() - interval '7 hours')");

        BatchJournaux.Execution ex = batch.executer("MANUEL", "admin");

        assertThat(ex.statut()).isEqualTo("TERMINE");
        assertThat(jdbc.queryForObject("select statut from batch_execution where declencheur = 'PLANIFIE'", String.class))
                .isEqualTo("INTERROMPU");
    }

    @Test
    void executionPlanifieeChaqueJourALHeureParametree() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate demain = LocalDate.now(zone).plusDays(1);
        Instant quatreHeures = demain.atTime(4, 0).atZone(zone).toInstant();

        assertThat(batch.executionPlanifieeDue(quatreHeures.minusSeconds(60))).isFalse();
        assertThat(batch.executionPlanifieeDue(quatreHeures)).isTrue();
        // Serveur arrêté à 4 h : rattrapage à son redémarrage, dans la limite de 6 heures.
        assertThat(batch.executionPlanifieeDue(quatreHeures.plus(Duration.ofHours(5)))).isTrue();
        assertThat(batch.executionPlanifieeDue(quatreHeures.plus(Duration.ofHours(7)))).isFalse();

        // Déjà faite aujourd'hui : pas de deuxième passage (une exécution manuelle ne compte pas).
        jdbc.update("insert into batch_execution (traitement, declencheur, statut, debut) values ('EJOURNAL', 'MANUEL', 'TERMINE', ?)",
                OffsetDateTime.ofInstant(quatreHeures.plusSeconds(30), zone));
        assertThat(batch.executionPlanifieeDue(quatreHeures.plusSeconds(120))).isTrue();
        jdbc.update("insert into batch_execution (traitement, declencheur, statut, debut) values ('EJOURNAL', 'PLANIFIE', 'TERMINE', ?)",
                OffsetDateTime.ofInstant(quatreHeures.plusSeconds(60), zone));
        assertThat(batch.executionPlanifieeDue(quatreHeures.plusSeconds(120))).isFalse();

        assertThat(batch.prochaineExecution(quatreHeures.minusSeconds(3600)))
                .isEqualTo(OffsetDateTime.ofInstant(quatreHeures, zone));
        jdbc.update("update parametre_application set valeur = 'false' where cle = 'ejournal.batch.actif'");
        assertThat(batch.executionPlanifieeDue(quatreHeures)).isFalse();
        assertThat(batch.prochaineExecution(quatreHeures)).isNull();
    }

    @Test
    void apiEtatParametrageEtLancement() throws Exception {
        Files.createDirectories(entree);
        Files.write(entree.resolve(NOM), journal());

        mvc.perform(get("/api/ingestion/ejournal/batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.heure").value("04:00"))
                .andExpect(jsonPath("$.nbFichiersEnAttente").value(1))
                .andExpect(jsonPath("$.enCours").value(false));

        mvc.perform(put("/api/ingestion/ejournal/batch/configuration").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actif":true,"heure":"25:00","repertoireEntree":"a","repertoireArchive":"b","repertoireRejet":"c"}"""))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/ingestion/ejournal/batch/configuration").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actif":true,"heure":"05:30","repertoireEntree":"%s","repertoireArchive":"%s","repertoireRejet":"%s"}"""
                                .formatted(entree, archive, rejet)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heure").value("05:30"));

        mvc.perform(post("/api/ingestion/ejournal/batch/executions").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNumber());
        // Exécution en arrière-plan : attente de sa fin.
        for (int i = 0; i < 100 && jdbc.queryForObject(
                "select count(*) from batch_execution where statut = 'EN_COURS'", Integer.class) > 0; i++) {
            Thread.sleep(100);
        }
        mvc.perform(get("/api/ingestion/ejournal/batch"))
                .andExpect(jsonPath("$.nbFichiersEnAttente").value(0))
                .andExpect(jsonPath("$.dernieresExecutions[0].declencheur").value("MANUEL"))
                .andExpect(jsonPath("$.dernieresExecutions[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$.dernieresExecutions[0].detail[0].resultat").value("INTEGRE"));
    }

    @Test
    void reserveAuMenuChargementDesJournaux() throws Exception {
        mvc.perform(get("/api/ingestion/ejournal/batch")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("lecteur").authorities(() -> "UTILISATEUR")))
                .andExpect(status().isForbidden());
    }
}
