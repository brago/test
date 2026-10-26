package com.rapmonet.securite.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.Securite;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

/** Gestion des comptes utilisateurs par l'administrateur. */
@Service
public class UtilisateursService {

    public record Utilisateur(String login, String nom, String agence, String email, String profilCode,
                              String libelleProfil, boolean actif, boolean verrouille, boolean doitChangerMdp,
                              OffsetDateTime derniereConnexion, OffsetDateTime creeLe, boolean deuxFacteurs,
                              OffsetDateTime mdpModifieLe) {
    }

    public record Creation(String login, String nom, String agence, String email, String profilCode,
                           String motDePasseTemporaire) {
    }

    public record Modification(String nom, String agence, String email, String profilCode, boolean actif) {
    }

    private static final Pattern LOGIN = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    private final JdbcClient jdbc;
    private final PasswordEncoder encodeur;
    private final AuditService audit;
    private final PolitiqueSecurite politique;
    private final MotsDePasse motsDePasse;
    private final AuthentificationService authentification;

    UtilisateursService(JdbcClient jdbc, PasswordEncoder encodeur, AuditService audit, PolitiqueSecurite politique,
                        MotsDePasse motsDePasse, AuthentificationService authentification) {
        this.jdbc = jdbc;
        this.encodeur = encodeur;
        this.audit = audit;
        this.politique = politique;
        this.motsDePasse = motsDePasse;
        this.authentification = authentification;
    }

    private static final String SELECT = """
            select u.login, u.nom, u.agence, u.email, u.profil_code, p.libelle as libelle_profil, u.actif,
                   u.verrouille, u.doit_changer_mdp, u.derniere_connexion, u.cree_le,
                   (u.totp_secret is not null) as deux_facteurs, u.mdp_modifie_le
            from securite_utilisateur u join securite_profil p on p.code = u.profil_code""";

    @Transactional(readOnly = true)
    public List<Utilisateur> lister() {
        return jdbc.sql(SELECT + " order by u.login").query(Utilisateur.class).list();
    }

    @Transactional(readOnly = true)
    public Utilisateur trouver(String login) {
        return jdbc.sql(SELECT + " where u.login = ?").param(login).query(Utilisateur.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur inconnu : " + login));
    }

    @Transactional
    public Utilisateur creer(Creation c) {
        if (c.login() == null || !LOGIN.matcher(c.login()).matches()) {
            throw new RegleMetierException("Identifiant invalide : 3 à 50 caractères (lettres, chiffres, . _ -).");
        }
        exigerNom(c.nom());
        verifierProfil(c.profilCode());
        if (jdbc.sql("select count(*) from securite_utilisateur where lower(login) = lower(?)").param(c.login())
                .query(Integer.class).single() > 0) {
            throw new RegleMetierException("L'identifiant « " + c.login() + " » existe déjà.");
        }
        politique.verifierComposition(c.login(), c.motDePasseTemporaire());
        jdbc.sql("""
                        insert into securite_utilisateur (login, nom, agence, email, profil_code, mot_de_passe, doit_changer_mdp)
                        values (?, ?, ?, ?, ?, ?, true)""")
                .params(c.login(), c.nom().strip(), vide(c.agence()), vide(c.email()), c.profilCode(),
                        encodeur.encode(c.motDePasseTemporaire()))
                .update();
        audit.enregistrer(ActionsAudit.UTILISATEUR_CREATION, c.login(), "Profil " + c.profilCode());
        return trouver(c.login());
    }

    @Transactional
    public Utilisateur modifier(String login, Modification m) {
        Utilisateur avant = trouver(login);
        exigerNom(m.nom());
        verifierProfil(m.profilCode());
        if (login.equalsIgnoreCase(Securite.login()) && (!m.actif() || !m.profilCode().equals(avant.profilCode()))) {
            throw new RegleMetierException("Vous ne pouvez pas désactiver votre propre compte ni changer votre profil.");
        }
        jdbc.sql("""
                        update securite_utilisateur set nom = ?, agence = ?, email = ?, profil_code = ?, actif = ?,
                            modifie_le = now() where login = ?""")
                .params(m.nom().strip(), vide(m.agence()), vide(m.email()), m.profilCode(), m.actif(), login)
                .update();
        StringBuilder detail = new StringBuilder();
        if (!avant.profilCode().equals(m.profilCode())) {
            detail.append("Profil ").append(avant.profilCode()).append(" → ").append(m.profilCode()).append(". ");
        }
        if (avant.actif() != m.actif()) {
            detail.append(m.actif() ? "Activé." : "Désactivé.");
        }
        audit.enregistrer(ActionsAudit.UTILISATEUR_MODIFICATION, login, detail.isEmpty() ? null : detail.toString().strip());
        return trouver(login);
    }

    @Transactional
    public Utilisateur deverrouiller(String login) {
        trouver(login);
        jdbc.sql("update securite_utilisateur set verrouille = false, nb_echecs = 0, modifie_le = now() where login = ?")
                .param(login).update();
        audit.enregistrer(ActionsAudit.UTILISATEUR_DEVERROUILLAGE, login, null);
        return trouver(login);
    }

    /** Réinitialise le mot de passe ; déverrouille aussi le compte. */
    @Transactional
    public Utilisateur reinitialiserMotDePasse(String login, String nouveau, boolean doitChanger) {
        trouver(login);
        motsDePasse.changer(login, nouveau, false, doitChanger);
        jdbc.sql("update securite_utilisateur set verrouille = false, nb_echecs = 0 where login = ?").param(login).update();
        audit.enregistrer(ActionsAudit.UTILISATEUR_REINITIALISATION_MDP, login,
                doitChanger ? "À modifier à la prochaine connexion" : null);
        return trouver(login);
    }

    /** Téléphone perdu ou changé : l'utilisateur devra réactiver la double authentification. */
    @Transactional
    public Utilisateur reinitialiserDeuxFacteurs(String login) {
        Utilisateur u = trouver(login);
        if (!u.deuxFacteurs()) {
            throw new RegleMetierException("La double authentification n'est pas activée sur ce compte.");
        }
        authentification.effacerDeuxFacteurs(login);
        audit.enregistrer(ActionsAudit.DEUX_FACTEURS_REINITIALISATION, login, null);
        return trouver(login);
    }

    private void verifierProfil(String profil) {
        if (profil == null || jdbc.sql("select count(*) from securite_profil where code = ?").param(profil)
                .query(Integer.class).single() == 0) {
            throw new RegleMetierException("Profil inconnu : " + profil);
        }
    }

    private static void exigerNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new RegleMetierException("Le nom est obligatoire.");
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
