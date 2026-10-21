package com.rapmonet.ingestion;

import java.util.List;

/** Intégration et gestion des fichiers LIS. */
public interface IntegrationLis {

    /** Fichier reçu (contenu brut). */
    record FichierRecu(String nom, TypeLis typeLis, byte[] contenu) {
    }

    /** Intègre un ou plusieurs fichiers : tout ou rien. */
    RapportIntegration integrer(List<FichierRecu> fichiers, String utilisateur);

    List<FichierLis> lister();

    List<RapportIntegration.AnomalieLis> anomalies(long fichierId);

    /** Supprime un fichier intégré et toutes ses données. */
    void annuler(long fichierId);
}
