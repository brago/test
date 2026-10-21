package com.rapmonet.securite.internal;

import com.rapmonet.securite.UtilisateurConnecte;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Recharge à chaque requête l'utilisateur de la session : un compte désactivé ou verrouillé
 * perd immédiatement l'accès, et une modification de profil s'applique sans reconnexion.
 */
class FiltreRafraichissementSession extends OncePerRequestFilter {

    private final AuthentificationService authentification;

    FiltreRafraichissementSession(AuthentificationService authentification) {
        this.authentification = authentification;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UtilisateurConnecte u) {
            Optional<UtilisateurConnecte> actuel = authentification.recharger(u.login());
            if (actuel.isEmpty()) {
                SecurityContextHolder.clearContext();
                HttpSession session = requete.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            } else {
                var contexte = SecurityContextHolder.createEmptyContext();
                contexte.setAuthentication(AuthentificationService.jeton(actuel.get()));
                SecurityContextHolder.setContext(contexte);
            }
        }
        chaine.doFilter(requete, reponse);
    }
}
