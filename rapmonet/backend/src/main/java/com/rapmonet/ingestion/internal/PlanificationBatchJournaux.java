package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RegleMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * Déclenche chaque jour le batch des journaux GAB à l'heure paramétrée. La vérification a lieu
 * chaque minute : un changement d'heure ou d'activation dans l'écran s'applique immédiatement, et
 * une exécution manquée (serveur arrêté) est rattrapée au redémarrage, dans les 6 heures.
 * Désactivable par la propriété {@code rapmonet.batch.planification-active=false} (tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rapmonet.batch.planification-active", havingValue = "true", matchIfMissing = true)
class PlanificationBatchJournaux {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificationBatchJournaux.class);

    private final BatchJournauxService batch;

    PlanificationBatchJournaux(BatchJournauxService batch) {
        this.batch = batch;
    }

    @Scheduled(cron = "0 * * * * *")
    void verifier() {
        try {
            if (batch.executionPlanifieeDue(Instant.now())) {
                LOG.info("Démarrage du batch des journaux GAB");
                batch.executer("PLANIFIE", BatchJournauxService.UTILISATEUR_BATCH);
            }
        } catch (RegleMetierException e) {
            LOG.info("Batch des journaux GAB non lancé : {}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Batch des journaux GAB : erreur de planification", e);
        }
    }
}
