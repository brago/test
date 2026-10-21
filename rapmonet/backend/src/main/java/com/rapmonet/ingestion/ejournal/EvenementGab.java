package com.rapmonet.ingestion.ejournal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Passage en mode superviseur (hors session client) : arrêté des compteurs, ajout d'espèces…
 *
 * @param actions    actions détectées : REMISE_A_ZERO_COMPTEURS, AJOUT_ESPECES, RETRAIT_CARTES_CAPTUREES
 * @param releves    relevés successifs des compteurs (ex. avant remise à zéro, puis après ajout d'espèces) ;
 *                   chaque relevé : libellé (CASSETTE, REJECTED, REMAINING, DISPENSED, TOTAL) → une valeur par type
 * @param especesAjoutees nombre de billets ajoutés par type de cassette (« CASH ADDED »)
 */
public record EvenementGab(int numeroLigne, LocalDateTime dateHeure, List<String> actions,
                           List<Map<String, List<Integer>>> releves, Map<String, Integer> especesAjoutees, String texte) {
}
