package com.rapmonet.commun.export;

import java.util.List;
import java.util.function.Consumer;

/**
 * Données d'un export : titre, critères de recherche, colonnes et lignes.
 *
 * @param lignes fournit les lignes une à une (permet de lire la base sans tout charger en mémoire)
 */
public record Tableau(String titre, List<String> criteres, String auteur, List<Colonne> colonnes,
                      Consumer<Consumer<List<Object>>> lignes) {

    public enum Type { TEXTE, ENTIER, MONTANT, DATE, DATE_HEURE }

    /**
     * Montant avec son sens comptable (convention Visa : « 2 183,96 DB »).
     * Excel reçoit le montant signé (débit négatif), le PDF le montant suivi du sens.
     */
    public record MontantSens(java.math.BigDecimal montant, String sens) {
        public java.math.BigDecimal signe() {
            return "DB".equals(sens) ? montant.negate() : montant;
        }
    }

    /** @param largeur largeur relative de la colonne dans le PDF */
    public record Colonne(String libelle, Type type, float largeur) {
    }
}
