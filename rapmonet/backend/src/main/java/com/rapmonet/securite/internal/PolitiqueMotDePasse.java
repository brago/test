package com.rapmonet.securite.internal;

import com.rapmonet.commun.web.RegleMetierException;

/** Règles de robustesse des mots de passe. */
final class PolitiqueMotDePasse {

    static final int LONGUEUR_MIN = 8;

    private PolitiqueMotDePasse() {
    }

    static void verifier(String login, String motDePasse) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN) {
            throw new RegleMetierException("Le mot de passe doit contenir au moins " + LONGUEUR_MIN + " caractères.");
        }
        if (motDePasse.chars().noneMatch(Character::isLetter) || motDePasse.chars().noneMatch(Character::isDigit)) {
            throw new RegleMetierException("Le mot de passe doit contenir au moins une lettre et un chiffre.");
        }
        if (motDePasse.equalsIgnoreCase(login)) {
            throw new RegleMetierException("Le mot de passe ne doit pas être identique à l'identifiant.");
        }
    }
}
