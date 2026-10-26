package com.rapmonet.securite;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

/**
 * Utilisateur de la session en cours.
 *
 * @param menus                    codes des menus autorisés par son profil (ex. EXPLORATION_LIS)
 * @param deuxFacteurs             double authentification activée sur le compte
 * @param doitActiverDeuxFacteurs  double authentification obligatoire mais pas encore activée
 * @param expirationMotDePasse     date d'expiration du mot de passe, null si pas d'expiration
 */
public record UtilisateurConnecte(String login, String nom, String agence, String profil, String libelleProfil,
                                  Set<String> menus, boolean doitChangerMotDePasse,
                                  boolean deuxFacteurs, boolean doitActiverDeuxFacteurs,
                                  LocalDate expirationMotDePasse) implements Serializable {

    /** Utilisateur sans double authentification ni expiration (traitements internes, tests). */
    public UtilisateurConnecte(String login, String nom, String agence, String profil, String libelleProfil,
                               Set<String> menus, boolean doitChangerMotDePasse) {
        this(login, nom, agence, profil, libelleProfil, menus, doitChangerMotDePasse, false, false, null);
    }
}
