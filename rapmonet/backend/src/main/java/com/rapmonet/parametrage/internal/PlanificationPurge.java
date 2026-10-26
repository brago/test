package com.rapmonet.parametrage.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;

/** Purge automatique : vérifiée chaque nuit à 02:30, exécutée une fois par mois si elle est activée. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rapmonet.batch.planification-active", havingValue = "true", matchIfMissing = true)
class PlanificationPurge {

    private static final Logger LOG = LoggerFactory.getLogger(PlanificationPurge.class);

    private final ConservationService conservation;

    PlanificationPurge(ConservationService conservation) {
        this.conservation = conservation;
    }

    @Scheduled(cron = "0 30 2 * * *")
    void verifier() {
        try {
            LocalDate aujourdhui = LocalDate.now();
            if (conservation.purgePlanifieeDue(aujourdhui)) {
                LOG.info("Démarrage de la purge automatique");
                conservation.purger("PLANIFIE", null, ConservationService.UTILISATEUR_PLANIFIE, aujourdhui);
            }
        } catch (RuntimeException e) {
            LOG.error("Purge automatique en erreur", e);
        }
    }
}
