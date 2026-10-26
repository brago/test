package com.rapmonet.rapprochement;

import java.time.LocalDate;

/** Synthèse du rapprochement GAB ↔ LIS sur une période, pour le tableau de bord. */
public interface SyntheseRapprochements {

    /**
     * Résultats de la période.
     *
     * @param ecartsATraiter écarts de la période non encore justifiés ni régularisés
     */
    record Synthese(int retraitsConfreres, int rapproches, int nonRemontes, long montantNonRemonte, int avisSansRetrait,
                    long montantAvisSansRetrait, int enAttente, int ecartsATraiter) {
    }

    Synthese synthese(LocalDate du, LocalDate au);
}
