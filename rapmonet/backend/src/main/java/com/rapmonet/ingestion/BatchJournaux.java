package com.rapmonet.ingestion;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Intégration automatique des journaux GAB déposés dans un dossier, chaque jour à heure fixe
 * (04:00 par défaut), ou à la demande.
 */
public interface BatchJournaux {

    record Configuration(boolean actif, String heure, String repertoireEntree, String repertoireArchive,
                         String repertoireRejet) {
    }

    /** @param resultat INTEGRE, DEJA_INTEGRE ou REJETE */
    record FichierTraite(String fichier, String resultat, String terminal, int nbOperations, int nbDoublons,
                         String message, String destination) {
    }

    record Execution(long id, String declencheur, String lancePar, OffsetDateTime debut, OffsetDateTime fin,
                     String statut, int nbFichiers, int nbIntegres, int nbDejaIntegres, int nbRejetes,
                     int nbOperations, String message, List<FichierTraite> detail) {
    }

    record Etat(Configuration configuration, OffsetDateTime prochaineExecution, boolean enCours,
                int nbFichiersEnAttente, List<Execution> dernieresExecutions) {
    }

    Etat etat();

    Configuration modifier(Configuration configuration, String utilisateur);

    /** Lance une exécution en arrière-plan ; refusé si une exécution est déjà en cours. */
    long lancer(String utilisateur);

    /** Exécute le traitement et attend sa fin (utilisé par la planification). */
    Execution executer(String declencheur, String utilisateur);
}
