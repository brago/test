package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.BatchJournaux;
import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Collectors;

/** Batch des journaux GAB : dépôt des GAB, archive par GAB et par mois. */
@Service
public class BatchJournauxService extends BatchDossier implements BatchJournaux {

    static final String TRAITEMENT = "EJOURNAL";

    private final IntegrationJournaux integration;

    BatchJournauxService(JdbcClient jdbc, JsonMapper json, IntegrationJournaux integration, JournalAudit audit,
                         @Qualifier("applicationTaskExecutor") TaskExecutor executeur,
                         @Value("${rapmonet.batch.delai-stabilite-secondes:120}") long delaiStabilite,
                         @Value("${rapmonet.batch.fuseau-horaire:}") String fuseau) {
        super(jdbc, json, audit, executeur, delaiStabilite, fuseau);
        this.integration = integration;
    }

    @Override
    String traitement() {
        return TRAITEMENT;
    }

    @Override
    String prefixe() {
        return "ejournal.batch";
    }

    @Override
    String libelle() {
        return "journaux GAB";
    }

    @Override
    String libelleFichier() {
        return "journal";
    }

    @Override
    Configuration parDefaut() {
        return new Configuration(true, "04:00", "donnees/ejournal/entree", "donnees/ejournal/archive",
                "donnees/ejournal/rejet");
    }

    @Override
    String actionAudit() {
        return ActionsAudit.BATCH_EJOURNAL;
    }

    @Override
    String actionAuditParametrage() {
        return ActionsAudit.PARAMETRAGE_BATCH;
    }

    @Override
    Traitement integrer(String nom, byte[] contenu) {
        IntegrationJournaux.Resultat r = integration.integrer(nom, contenu, UTILISATEUR_BATCH);
        boolean dejaIntegre = !r.integre() && r.anomalies().stream()
                .anyMatch(a -> a.gravite().equals("BLOQUANTE") && a.message().contains("déjà intégré"));
        String resultat = r.integre() ? "INTEGRE" : dejaIntegre ? "DEJA_INTEGRE" : "REJETE";
        String message = r.integre() ? null : r.anomalies().stream().filter(a -> a.gravite().equals("BLOQUANTE"))
                .map(a -> a.message()).collect(Collectors.joining(" ; "));
        String terminal = r.terminal() != null ? r.terminal() : "inconnu";
        String mois = (r.dateFin() != null ? r.dateFin() : LocalDate.now(fuseau)).toString().substring(0, 7);
        return new Traitement(resultat, r.terminal(), r.nbOperations(), r.nbDoublons(), message,
                r.anomalies().stream().map(a -> a.gravite() + " : " + a.message()).toList(), Path.of(terminal, mois));
    }
}
