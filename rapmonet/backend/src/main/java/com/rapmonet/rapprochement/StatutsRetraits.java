package com.rapmonet.rapprochement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Résultat du rapprochement GAB ↔ LIS de retraits du journal, pour le traitement d'une réclamation. */
public interface StatutsRetraits {

    record Retrait(long id, String terminal, LocalDateTime dateHeure, String carte, String reference, Long montant) {
    }

    /**
     * Statut d'un retrait.
     *
     * @param resultat    libellé du résultat (Rapproché, Retrait non remonté…) ou carte de la banque
     * @param traitement  traitement de l'écart (À traiter, Justifié, Régularisé), si c'est un écart
     */
    record Statut(String resultat, String traitement, String commentaire, String autorisationLis) {
    }

    /** Statut de chaque retrait, par identifiant d'opération. */
    Map<Long, Statut> statuts(List<Retrait> retraits);
}
