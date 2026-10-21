package com.rapmonet.chargeback.internal;

import com.rapmonet.ingestion.FichiersLisIntegres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Crée les dossiers de chargeback dès qu'un fichier LIS est intégré. */
@Component
class SynchronisationChargebacks {

    private static final Logger LOG = LoggerFactory.getLogger(SynchronisationChargebacks.class);

    private final ChargebacksService service;

    SynchronisationChargebacks(ChargebacksService service) {
        this.service = service;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fichiersLisIntegres(FichiersLisIntegres evenement) {
        int n = service.synchroniser();
        if (n > 0) {
            LOG.info("{} nouveau(x) chargeback(s) dans les fichiers LIS des journées {}", n, evenement.journees());
        }
    }
}
