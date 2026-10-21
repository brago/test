package com.rapmonet.securite;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Accès à l'utilisateur de la requête en cours. */
public final class Securite {

    private Securite() {
    }

    public static Optional<UtilisateurConnecte> utilisateur() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UtilisateurConnecte u ? Optional.of(u) : Optional.empty();
    }

    public static String login() {
        return utilisateur().map(UtilisateurConnecte::login).orElse(null);
    }
}
