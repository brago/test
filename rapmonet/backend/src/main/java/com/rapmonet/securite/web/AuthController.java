package com.rapmonet.securite.web;

import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import com.rapmonet.securite.UtilisateurConnecte;
import com.rapmonet.securite.internal.AuthentificationService;
import com.rapmonet.securite.internal.AuthentificationService.ConnexionRefuseeException;
import com.rapmonet.securite.internal.AuthentificationService.Enrolement;
import com.rapmonet.securite.internal.AuthentificationService.EtapeConnexion;
import com.rapmonet.securite.internal.PolitiqueSecurite;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    record Identifiants(String login, String motDePasse) {
    }

    record ChangementMotDePasse(String actuel, String nouveau) {
    }

    record Code(String code) {
    }

    record MotDePasse(String motDePasse) {
    }

    /** Mot de passe correct : le code de double authentification est attendu. */
    record CodeRequis(boolean codeRequis) {
    }

    /** Identifiant en attente du code, conservé en session pendant 5 minutes au plus. */
    private record ConnexionEnAttente(String login, Instant debut) implements Serializable {
    }

    static final String SESSION_CONNEXION = "rapmonet.connexion.en-attente";
    static final String SESSION_ENROLEMENT = "rapmonet.deux-facteurs.secret";
    private static final Duration DELAI_CODE = Duration.ofMinutes(5);

    private final AuthentificationService authentification;
    private final PolitiqueSecurite politique;
    private final SecurityContextRepository depot;
    private final JournalAudit audit;

    AuthController(AuthentificationService authentification, PolitiqueSecurite politique,
                   SecurityContextRepository depot, JournalAudit audit) {
        this.authentification = authentification;
        this.politique = politique;
        this.depot = depot;
        this.audit = audit;
    }

    /** Renvoie l'utilisateur connecté, ou codeRequis si la double authentification est activée sur le compte. */
    @PostMapping("/connexion")
    Object connexion(@RequestBody Identifiants identifiants, HttpServletRequest requete, HttpServletResponse reponse) {
        EtapeConnexion etape = authentification.connecter(identifiants.login(), identifiants.motDePasse());
        if (etape.codeRequis()) {
            SecurityContextHolder.clearContext();
            HttpSession session = requete.getSession(true);
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            session.setAttribute(SESSION_CONNEXION, new ConnexionEnAttente(etape.login(), Instant.now()));
            return new CodeRequis(true);
        }
        ouvrirSession(etape.utilisateur(), requete, reponse);
        return etape.utilisateur();
    }

    @PostMapping("/code")
    UtilisateurConnecte code(@RequestBody Code code, HttpServletRequest requete, HttpServletResponse reponse) {
        HttpSession session = requete.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_CONNEXION) instanceof ConnexionEnAttente attente)) {
            throw new ConnexionRefuseeException("Session de connexion expirée : saisissez à nouveau votre mot de passe.");
        }
        if (attente.debut().plus(DELAI_CODE).isBefore(Instant.now())) {
            session.removeAttribute(SESSION_CONNEXION);
            throw new ConnexionRefuseeException("Délai dépassé : saisissez à nouveau votre mot de passe.");
        }
        UtilisateurConnecte u;
        try {
            u = authentification.verifierCode(attente.login(), code.code());
        } catch (ConnexionRefuseeException e) {
            if (e.getMessage().contains("verrouillé")) {
                session.removeAttribute(SESSION_CONNEXION);
            }
            throw e;
        }
        session.removeAttribute(SESSION_CONNEXION);
        ouvrirSession(u, requete, reponse);
        return u;
    }

    /** Règles de composition des mots de passe en vigueur, pour l'écran de changement. */
    @GetMapping("/politique-mot-de-passe")
    List<PolitiqueSecurite.Regle> reglesMotDePasse() {
        return politique.regles();
    }

    @PostMapping("/deux-facteurs/enrolement")
    Enrolement enrolement(HttpServletRequest requete) {
        byte[] secret = AuthentificationService.nouveauSecret();
        requete.getSession().setAttribute(SESSION_ENROLEMENT, secret);
        return authentification.enrolement(Securite.login(), secret);
    }

    @PostMapping("/deux-facteurs/activation")
    UtilisateurConnecte activation(@RequestBody Code code, HttpServletRequest requete, HttpServletResponse reponse) {
        HttpSession session = requete.getSession();
        UtilisateurConnecte u = authentification.activerDeuxFacteurs(Securite.login(),
                (byte[]) session.getAttribute(SESSION_ENROLEMENT), code.code());
        session.removeAttribute(SESSION_ENROLEMENT);
        enregistrerSession(u, requete, reponse);
        return u;
    }

    @PostMapping("/deux-facteurs/desactivation")
    UtilisateurConnecte desactivation(@RequestBody MotDePasse m, HttpServletRequest requete, HttpServletResponse reponse) {
        UtilisateurConnecte u = authentification.desactiverDeuxFacteurs(Securite.login(), m.motDePasse());
        enregistrerSession(u, requete, reponse);
        return u;
    }

    /** Nouvel identifiant de session après authentification (protection contre la fixation de session). */
    private void ouvrirSession(UtilisateurConnecte u, HttpServletRequest requete, HttpServletResponse reponse) {
        if (requete.getSession(false) != null) {
            requete.changeSessionId();
        } else {
            requete.getSession(true);
        }
        enregistrerSession(u, requete, reponse);
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
