package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Résultat d'une demande d'intégration de fichiers LIS.
 *
 * @param integre vrai si tous les fichiers ont été intégrés ; faux si au moins une anomalie
 *                bloquante a été détectée (aucun fichier n'est alors intégré)
 */
public record RapportIntegration(boolean integre, List<RapportFichier> fichiers) {

    public record RapportFichier(String nom, TypeLis typeLis, Long id, LocalDate journee,
                                 int nbEnregistrements, int nbTransactions, Map<String, Integer> repartitionTc,
                                 List<AnomalieLis> anomalies) {
    }

    /** @param gravite BLOQUANTE ou AVERTISSEMENT */
    public record AnomalieLis(Integer ligne, String gravite, String message) {
    }
}
