package com.rapmonet.ingestion.moteur;

import java.util.List;
import java.util.Map;

/**
 * Transaction : un TCR 0 suivi des TCR du même TC.
 *
 * @param numero               rang de la transaction dans le fichier (à partir de 1)
 * @param attributsSpecifiques attributs marqués « spécifiques » de toutes ses lignes
 */
public record TransactionLue(int numero, String sousFichier, String tc, List<LigneLue> lignes,
                             Map<String, Object> attributsSpecifiques) {

    public int sequence() {
        return lignes.getFirst().sequence();
    }
}
