package com.rapmonet.ingestion.moteur;

import java.util.List;
import java.util.Map;

/**
 * Résultat de la lecture d'un fichier.
 *
 * @param lignes       toutes les lignes, y compris en-têtes et fins
 * @param entete       attributs de l'en-tête de fichier
 * @param fin          attributs de la fin de fichier
 */
public record ResultatLecture(List<LigneLue> lignes, List<TransactionLue> transactions,
                              Map<String, Object> entete, Map<String, Object> fin,
                              List<Anomalie> anomalies) {

    public boolean estBloquant() {
        return anomalies.stream().anyMatch(a -> a.gravite() == Gravite.BLOQUANTE);
    }

    /** Numéro de transaction de chaque ligne (null pour les en-têtes et fins). */
    public Map<Integer, Integer> transactionParLigne() {
        var map = new java.util.HashMap<Integer, Integer>();
        transactions.forEach(t -> t.lignes().forEach(l -> map.put(l.numero(), t.numero())));
        return map;
    }
}
