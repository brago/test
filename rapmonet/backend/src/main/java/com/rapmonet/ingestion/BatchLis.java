package com.rapmonet.ingestion;

import com.rapmonet.ingestion.BatchJournaux.Configuration;
import com.rapmonet.ingestion.BatchJournaux.Etat;
import com.rapmonet.ingestion.BatchJournaux.Execution;

/**
 * Intégration automatique des fichiers LIS (SW, PR) déposés dans un dossier, chaque jour à heure fixe (08:00 par
 * défaut) ou à la demande. Même configuration, historique et états que le batch des journaux GAB.
 */
public interface BatchLis {

    Etat etat();

    Configuration modifier(Configuration configuration, String utilisateur);

    /** Lance une exécution en arrière-plan ; refusé si une exécution est déjà en cours. */
    long lancer(String utilisateur);

    /** Exécute le traitement et attend sa fin (utilisé par la planification). */
    Execution executer(String declencheur, String utilisateur);
}
