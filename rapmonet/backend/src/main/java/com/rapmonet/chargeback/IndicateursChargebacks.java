package com.rapmonet.chargeback;

import java.time.LocalDate;

/** Indicateurs des chargebacks, pour le tableau de bord. */
public interface IndicateursChargebacks {

    /**
     * Chargebacks reçus sur la période et dossiers en cours à date.
     *
     * @param enCours         dossiers non archivés
     * @param delaiEcoule     dossiers non archivés dont le délai de représentation est dépassé sans représentation
     */
    record Indicateurs(int recusEmission, long montantRecusEmission, int recusAcquisition, long montantRecusAcquisition,
                       int enCours, long montantEnCours, int delaiEcoule) {
    }

    Indicateurs indicateurs(LocalDate du, LocalDate au);
}
