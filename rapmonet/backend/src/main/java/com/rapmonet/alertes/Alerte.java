package com.rapmonet.alertes;

/**
 * Une situation qui demande une action.
 *
 * @param code    identifiant stable de l'alerte
 * @param nombre  nombre d'éléments concernés
 * @param montant montant concerné en XOF, nul si sans objet
 * @param menu    menu qui donne accès au traitement : seuls les utilisateurs qui l'ont voient l'alerte
 * @param lien    écran où traiter l'alerte
 */
public record Alerte(String code, Niveau niveau, String titre, String detail, long nombre, Long montant, String menu,
                     String lien) {

    /** CRITIQUE : à traiter tout de suite ; ATTENTION : à traiter dans la journée ; INFO : à suivre. */
    public enum Niveau { CRITIQUE, ATTENTION, INFO }
}
