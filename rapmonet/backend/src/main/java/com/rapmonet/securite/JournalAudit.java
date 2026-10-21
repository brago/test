package com.rapmonet.securite;

/**
 * Piste d'audit : trace les actions significatives des utilisateurs.
 * L'utilisateur et l'adresse IP sont ceux de la requête en cours.
 */
public interface JournalAudit {

    void enregistrer(String action, String objet, String detail);

    void enregistrer(String action, String objet, String detail, boolean succes);

    /** Pour les événements où l'utilisateur n'est pas (encore) authentifié, ex. échec de connexion. */
    void enregistrerPour(String login, String action, String objet, String detail, boolean succes);
}
