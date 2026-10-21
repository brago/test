package com.rapmonet.securite.web;

import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import com.rapmonet.securite.UtilisateurConnecte;
import com.rapmonet.securite.internal.AuthentificationService;
import com.rapmonet.securite.internal.AuthentificationService.ConnexionRefuseeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    record Identifiants(String login, String motDePasse) {
    }

    record ChangementMotDePasse(String actuel, String nouveau) {
    }

    private final AuthentificationService authentification;
    private final SecurityContextRepository depot;
    private final JournalAudit audit;

    AuthController(AuthentificationService authentification, SecurityContextRepository depot, JournalAudit audit) {
        this.authentification = authentification;
        this.depot = depot;
        this.audit = audit;
    }

    @PostMapping("/connexion")
    UtilisateurConnecte connexion(@RequestBody Identifiants identifiants, HttpServletRequest requete,
                                  HttpServletResponse reponse) {
        UtilisateurConnecte u = authentification.connecter(identifiants.login(), identifiants.motDePasse());
        // Nouvel identifiant de session après authentification (protection contre la fixation de session).
        if (requete.getSession(false) != null) {
            requete.changeSessionId();
        } else {
            requete.getSession(true);
        }
        enregistrerSession(u, requete, reponse);
        return u;
    }

    @GetMapping("/moi")
    ResponseEntity<UtilisateurConnecte> moi() {
        return Securite.utilisateur().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/mot-de-passe")
    UtilisateurConnecte changerMotDePasse(@RequestBody ChangementMotDePasse demande, HttpServletRequest requete,
                                          HttpServletResponse reponse) {
        UtilisateurConnecte u = authentification.changerMotDePasse(Securite.login(), demande.actuel(), demande.nouveau());
        enregistrerSession(u, requete, reponse);
        return u;
    }

    @PostMapping("/deconnexion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deconnexion(HttpServletRequest requete) {
        audit.enregistrer(ActionsAudit.DECONNEXION, Securite.login(), null);
        HttpSession session = requete.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @ExceptionHandler(ConnexionRefuseeException.class)
    ProblemDetail connexionRefusee(ConnexionRefuseeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    private void enregistrerSession(UtilisateurConnecte u, HttpServletRequest requete, HttpServletResponse reponse) {
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(AuthentificationService.jeton(u));
        SecurityContextHolder.setContext(contexte);
        depot.saveContext(contexte, requete, reponse);
    }
}
