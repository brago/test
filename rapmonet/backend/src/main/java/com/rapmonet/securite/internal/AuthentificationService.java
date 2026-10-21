package com.rapmonet.securite.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.Habilitations;
import com.rapmonet.securite.UtilisateurConnecte;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthentificationService {

    /** Nombre d'échecs consécutifs qui verrouille le compte. */
    static final int ECHECS_MAX = 3;

    static final String MESSAGE_IDENTIFIANTS = "Identifiant ou mot de passe incorrect.";

    /** Connexion refusée ; le message est affichable à l'utilisateur. */
    public static class ConnexionRefuseeException extends RuntimeException {
        public ConnexionRefuseeException(String message) {
            super(message);
        }
    }

    private record Compte(String login, String nom, String agence, String profilCode, String libelleProfil,
                          String motDePasse, boolean doitChangerMdp, boolean actif, boolean verrouille, int nbEchecs) {
    }

    private static final String SELECT_COMPTE = """
            select u.login, u.nom, u.agence, u.profil_code, p.libelle as libelle_profil, u.mot_de_passe,
                   u.doit_changer_mdp, u.actif, u.verrouille, u.nb_echecs
            from securite_utilisateur u join securite_profil p on p.code = u.profil_code
            where lower(u.login) = lower(?)""";

    private final JdbcClient jdbc;
    private final PasswordEncoder encodeur;
    private final AuditService audit;
    private final String empreinteFactice;

    AuthentificationService(JdbcClient jdbc, PasswordEncoder encodeur, AuditService audit) {
        this.jdbc = jdbc;
        this.encodeur = encodeur;
        this.audit = audit;
        this.empreinteFactice = encodeur.encode("compte-inexistant");
    }

    /**
     * Vérifie l'identifiant et le mot de passe. Après {@value #ECHECS_MAX} échecs consécutifs,
     * le compte est verrouillé jusqu'à son déverrouillage par un administrateur.
     */
    @Transactional(noRollbackFor = ConnexionRefuseeException.class)
    public UtilisateurConnecte connecter(String login, String motDePasse) {
        String saisi = login == null ? "" : login.strip();
        Optional<Compte> trouve = compte(saisi);
        if (trouve.isEmpty()) {
            // Même coût de calcul qu'un compte existant : ne révèle pas si l'identifiant existe.
            encodeur.matches(motDePasse == null ? "" : motDePasse, empreinteFactice);
            audit.enregistrerPour(saisi, ActionsAudit.CONNEXION, saisi, "Identifiant inconnu", false);
            throw new ConnexionRefuseeException(MESSAGE_IDENTIFIANTS);
        }
        Compte c = trouve.get();
        if (c.verrouille()) {
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Compte verrouillé", false);
            throw new ConnexionRefuseeException("Compte verrouillé après " + ECHECS_MAX
                    + " tentatives infructueuses. Contactez l'administrateur.");
        }
        if (!c.actif()) {
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Compte désactivé", false);
            throw new ConnexionRefuseeException("Compte désactivé. Contactez l'administrateur.");
        }
        if (motDePasse == null || !encodeur.matches(motDePasse, c.motDePasse())) {
            int echecs = c.nbEchecs() + 1;
            boolean verrouiller = echecs >= ECHECS_MAX;
            jdbc.sql("update securite_utilisateur set nb_echecs = ?, verrouille = ? where login = ?")
                    .params(echecs, verrouiller, c.login()).update();
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Mot de passe incorrect (" + echecs + ")", false);
            if (verrouiller) {
                audit.enregistrerPour(c.login(), ActionsAudit.VERROUILLAGE, c.login(), ECHECS_MAX + " échecs consécutifs", true);
                throw new ConnexionRefuseeException("Compte verrouillé après " + ECHECS_MAX
                        + " tentatives infructueuses. Contactez l'administrateur.");
            }
            throw new ConnexionRefuseeException(MESSAGE_IDENTIFIANTS);
        }
        jdbc.sql("update securite_utilisateur set nb_echecs = 0, derniere_connexion = now() where login = ?")
                .param(c.login()).update();
        audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), null, true);
        return versUtilisateur(c);
    }

    /** Recharge l'utilisateur ; vide s'il n'existe plus, est désactivé ou verrouillé. */
    @Transactional(readOnly = true)
    public Optional<UtilisateurConnecte> recharger(String login) {
        return compte(login).filter(c -> c.actif() && !c.verrouille()).map(this::versUtilisateur);
    }

    @Transactional
    public UtilisateurConnecte changerMotDePasse(String login, String actuel, String nouveau) {
        Compte c = compte(login).orElseThrow(() -> new RegleMetierException("Utilisateur inconnu."));
        if (actuel == null || !encodeur.matches(actuel, c.motDePasse())) {
            audit.enregistrerPour(c.login(), ActionsAudit.CHANGEMENT_MOT_DE_PASSE, c.login(), "Mot de passe actuel incorrect", false);
            throw new RegleMetierException("Le mot de passe actuel est incorrect.");
        }
        PolitiqueMotDePasse.verifier(c.login(), nouveau);
        if (encodeur.matches(nouveau, c.motDePasse())) {
            throw new RegleMetierException("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        jdbc.sql("update securite_utilisateur set mot_de_passe = ?, doit_changer_mdp = false, modifie_le = now() where login = ?")
                .params(encodeur.encode(nouveau), c.login()).update();
        audit.enregistrerPour(c.login(), ActionsAudit.CHANGEMENT_MOT_DE_PASSE, c.login(), null, true);
        return recharger(c.login()).orElseThrow();
    }

    /** Jeton Spring Security correspondant à l'utilisateur. */
    public static UsernamePasswordAuthenticationToken jeton(UtilisateurConnecte u) {
        List<GrantedAuthority> autorisations = new ArrayList<>();
        if (u.doitChangerMotDePasse()) {
            autorisations.add(new SimpleGrantedAuthority(Habilitations.CHANGEMENT_MDP_REQUIS));
        } else {
            autorisations.add(new SimpleGrantedAuthority(Habilitations.UTILISATEUR));
            u.menus().forEach(m -> autorisations.add(new SimpleGrantedAuthority(Habilitations.menu(m))));
        }
        return UsernamePasswordAuthenticationToken.authenticated(u, null, autorisations);
    }

    private Optional<Compte> compte(String login) {
        return jdbc.sql(SELECT_COMPTE).param(login).query(Compte.class).optional();
    }

    private UtilisateurConnecte versUtilisateur(Compte c) {
        Set<String> menus = new LinkedHashSet<>(jdbc.sql("""
                        select pm.menu_code from securite_profil_menu pm join securite_menu m on m.code = pm.menu_code
                        where pm.profil_code = ? order by m.rang""")
                .param(c.profilCode()).query(String.class).list());
        return new UtilisateurConnecte(c.login(), c.nom(), c.agence(), c.profilCode(), c.libelleProfil(),
                Set.copyOf(menus), c.doitChangerMdp());
    }
}
