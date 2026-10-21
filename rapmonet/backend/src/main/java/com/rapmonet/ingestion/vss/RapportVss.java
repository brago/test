package com.rapmonet.ingestion.vss;

import java.time.LocalDate;
import java.util.List;

/**
 * Rapport VSS lu (ex. VSS-110 « SETTLEMENT SUMMARY REPORT »), toutes pages réunies.
 *
 * @param sansDonnees le rapport indique « NO DATA FOR THIS REPORT »
 * @param complet     la ligne « END OF … REPORT » a été trouvée
 */
public record RapportVss(String code, String titre, String sreId, String sreNom, String rollup, String entiteFonds,
                         LocalDate dateTraitement, LocalDate dateRapport, int nbPages, boolean sansDonnees,
                         boolean complet, List<String> colonnes, List<LigneVss> lignes) {
}
