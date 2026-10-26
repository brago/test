package com.rapmonet.securite;

/** Noms des autorisations Spring Security utilisées par l'application. */
public final class Habilitations {

    /** Session complète (mot de passe à jour). */
    public static final String UTILISATEUR = "UTILISATEUR";

    /** Session limitée au changement de mot de passe (première connexion ou mot de passe réinitialisé). */
    public static final String CHANGEMENT_MDP_REQUIS = "CHANGEMENT_MDP_REQUIS";

    /** Session limitée à l'activation de la double authentification, quand elle est obligatoire. */
    public static final String ACTIVATION_2FA_REQUISE = "ACTIVATION_2FA_REQUISE";

    /** Préfixe des autorisations dérivées des menus du profil : MENU_EXPLORATION_LIS… */
    public static final String PREFIXE_MENU = "MENU_";

    private Habilitations() {
    }

    public static String menu(String code) {
        return PREFIXE_MENU + code;
    }
}
