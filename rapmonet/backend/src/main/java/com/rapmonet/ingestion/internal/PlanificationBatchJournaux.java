package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RegleMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;

/**
 * Déclenche chaque jour les batchs (journaux GAB, fichiers LIS) à l'heure paramétrée. La vérification a lieu
 * chaque minute : un changement d'heure ou d'activation dans l'écran s'applique immédiatement, et une exécution
 * manquée (serveur arrêté) est rattrapée au redémarrage, dans les 6 heures.
 * Désactivable par la propriété rapmonet.batch.planification-active=false (tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rapmonet.batch.planification-active", havingValue = "true", matchIfMissing = true)
class PlanificationBatchJournaux {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificationBatchJournaux.class);

    private final List<BatchDossier> batchs;

    PlanificationBatchJournaux(BatchJournauxService journaux, BatchLisService lis) {
        this.batchs = List.of(journaux, lis);
    }

    @Scheduled(cron = "0 * * * * *")
    void verifier() {
        for (BatchDossier batch : batchs) {
            try {
                if (batch.executionPlanifieeDue(Instant.now())) {
                    LOG.info("Démarrage du batch des {}", batch.libelle());
                    batch.executer("PLANIFIE", BatchDossier.UTILISATEUR_BATCH);
                }
            } catch (RegleMetierException e) {
                LOG.info("Batch des {} non lancé : {}", batch.libelle(), e.getMessage());
            } catch (RuntimeException e) {
                LOG.error("Batch des {} : erreur de planification", batch.libelle(), e);
            }
        }
    }
}
