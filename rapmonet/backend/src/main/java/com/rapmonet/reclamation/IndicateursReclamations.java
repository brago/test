package com.rapmonet.reclamation;

import java.time.LocalDate;

/** Indicateurs des réclamations porteurs, pour le tableau de bord. */
public interface IndicateursReclamations {

    /**
     * Réclamations reçues et closes sur la période ; ouvertes et en retard à date.
     *
     * @param delaiMoyenJours délai moyen entre ouverture et clôture des réclamations closes sur la période
     */
    record Indicateurs(int recues, long montantConteste, int closes, int fondees, double delaiMoyenJours, int ouvertes,
                       int enRetard) {
    }

    Indicateurs indicateurs(LocalDate du, LocalDate au);
}
