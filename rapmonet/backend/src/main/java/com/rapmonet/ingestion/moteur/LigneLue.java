package com.rapmonet.ingestion.moteur;

import java.util.Map;

/**
 * Ligne du fichier, découpée.
 *
 * @param numero     numéro de ligne dans le fichier (à partir de 1)
 * @param attributs  attributs décodés (ordre du paramétrage), valeurs nulles exclues
 */
public record LigneLue(int numero, String tc, int sequence, String tcr, String contenu,
                       Map<String, Object> attributs) {
}
