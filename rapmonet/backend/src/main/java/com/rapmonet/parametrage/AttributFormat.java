package com.rapmonet.parametrage;

/**
 * Attribut placé dans un TCR.
 *
 * @param position   position de début dans la ligne (1 = premier caractère)
 * @param specifique   vrai si la valeur est enregistrée avec la transaction
 * @param confidentiel vrai pour un numéro de carte : la valeur est masquée avant tout stockage
 */
public record AttributFormat(String code, String libelle, int position, int taille, Pad pad,
                             int decimales, TypeDonnee type, boolean specifique,
                             boolean confidentiel) {

    /** Position du dernier caractère (incluse). */
    public int positionFin() {
        return position + taille - 1;
    }
}
