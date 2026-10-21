package com.rapmonet.ingestion.vss;

import java.util.Map;

/**
 * Ligne de données d'un rapport VSS.
 *
 * @param niveau  profondeur dans la hiérarchie des libellés (0 = racine)
 * @param chemin  libellés parents, séparés par « > » (ex. « ISSUER TRANSACTIONS > PURCHASE »)
 * @param valeurs valeur de chaque colonne (nom de colonne déduit des en-têtes du rapport)
 */
public record LigneVss(int numeroLigne, int page, int niveau, String chemin, String libelle,
                       String deviseReglement, String deviseCompensation, Map<String, ValeurVss> valeurs) {
}
