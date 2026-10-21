package com.rapmonet.securite;

import java.io.Serializable;
import java.util.Set;

/**
 * Utilisateur de la session en cours.
 *
 * @param menus codes des menus autorisés par son profil (ex. EXPLORATION_LIS)
 */
public record UtilisateurConnecte(String login, String nom, String agence, String profil, String libelleProfil,
                                  Set<String> menus, boolean doitChangerMotDePasse) implements Serializable {
}
