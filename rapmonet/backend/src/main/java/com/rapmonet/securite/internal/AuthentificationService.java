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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthentificationService {

    static final String MESSAGE_IDENTIFIANTS = "Identifiant ou mot de passe incorrect.";
    static final String EMETTEUR = "RAPMONET";

    /** Connexion refusée ; le message est affichable à l'utilisateur. */
    public static class ConnexionRefuseeException extends RuntimeException {
        public ConnexionRefuseeException(String message) {
            super(message);
        }
    }

    /**
     * Résultat de la vérification du mot de passe.
     *
     * @param utilisateur utilisateur connecté, null si un code de double authentification est attendu
     */
    public record EtapeConnexion(UtilisateurConnecte utilisateur, String login) {
        public boolean codeRequis() {
            return utilisateur == null;
        }
    }

    /**
     * Secret proposé à l'activation de la double authentification.
     *
     * @param secret secret en base32, à saisir dans l'application si le QR code ne peut pas être lu
     * @param uri    adresse otpauth contenue dans le QR code
     * @param qrSvg  QR code au format SVG
     */
    public record Enrolement(String secret, String uri, String qrSvg) {
    }

    private record Compte(String login, String nom, String agence, String profilCode, String libelleProfil,
                          String motDePasse, boolean doitChangerMdp, boolean actif, boolean verrouille, int nbEchecs,
                          OffsetDateTime mdpModifieLe, String totpSecret, Long totpDernierPas) {
    }

    private static final String SELECT_COMPTE = """
            select u.login, u.nom, u.agence, u.profil_code, p.libelle as libelle_profil, u.mot_de_passe,
                   u.doit_changer_mdp, u.actif, u.verrouille, u.nb_echecs, u.mdp_modifie_le, u.totp_secret,
                   u.totp_dernier_pas
            from securite_utilisateur u join securite_profil p on p.code = u.profil_code
            where lower(u.login) = lower(?)""";

    private final JdbcClient jdbc;
    private final PasswordEncoder encodeur;
    private final AuditService audit;
    private final PolitiqueSecurite politique;
    private final MotsDePasse motsDePasse;
    private final ChiffrementSecrets chiffrement;
    private final Clock horloge;
    private final String empreinteFactice;

    AuthentificationService(JdbcClient jdbc, PasswordEncoder encodeur, AuditService audit, PolitiqueSecurite politique,
                            MotsDePasse motsDePasse, ChiffrementSecrets chiffrement) {
        this.jdbc = jdbc;
        this.encodeur = encodeur;
        this.audit = audit;
        this.politique = politique;
        this.motsDePasse = motsDePasse;
        this.chiffrement = chiffrement;
        this.horloge = Clock.systemDefaultZone();
        this.empreinteFactice = encodeur.encode("compte-inexistant");
    }

    /**
     * Vérifie l'identifiant et le mot de passe. Après le nombre d'échecs consécutifs fixé par la politique,
     * le compte est verrouillé jusqu'à son déverrouillage par un administrateur. Si la double authentification
     * est activée sur le compte, la connexion n'aboutit qu'après la saisie du code (voir verifierCode).
     */
    @Transactional(noRollbackFor = ConnexionRefuseeException.class)
    public EtapeConnexion connecter(String login, String motDePasse) {
        String saisi = login == null ? "" : login.strip();
        Optional<Compte> trouve = compte(saisi);
        if (trouve.isEmpty()) {
            // Même coût de calcul qu'un compte existant : ne révèle pas si l'identifiant existe.
            encodeur.matches(motDePasse == null ? "" : motDePasse, empreinteFactice);
            audit.enregistrerPour(saisi, ActionsAudit.CONNEXION, saisi, "Identifiant inconnu", false);
            throw new ConnexionRefuseeException(MESSAGE_IDENTIFIANTS);
        }
        Compte c = trouve.get();
        exigerCompteUtilisable(c);
        if (motDePasse == null || !encodeur.matches(motDePasse, c.motDePasse())) {
            echec(c, "Mot de passe incorrect", MESSAGE_IDENTIFIANTS);
        }
        if (c.totpSecret() != null) {
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Mot de passe correct, code attendu", true);
            return new EtapeConnexion(null, c.login());
        }
        return new EtapeConnexion(reussir(c, null), c.login());
    }

    /** Second facteur : code à 6 chiffres de l'application d'authentification. Les échecs comptent pour le verrouillage. */
    @Transactional(noRollbackFor = ConnexionRefuseeException.class)
    public UtilisateurConnecte verifierCode(String login, String code) {
        Compte c = compte(login).orElseThrow(() -> new ConnexionRefuseeException("Session de connexion expirée."));
        exigerCompteUtilisable(c);
        if (c.totpSecret() == null) {
            throw new ConnexionRefuseeException("Session de connexion expirée.");
        }
        long pas = Totp.verifier(chiffrement.dechiffrer(c.totpSecret()), code, c.totpDernierPas(), horloge.instant());
        if (pas < 0) {
            echec(c, "Code de double authentification incorrect", "Code incorrect ou expiré.");
        }
        return reussir(c, pas);
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
        motsDePasse.changer(c.login(), nouveau, true, false);
        audit.enregistrerPour(c.login(), ActionsAudit.CHANGEMENT_MOT_DE_PASSE, c.login(), null, true);
        return recharger(c.login()).orElseThrow();
    }

    /** Nouveau secret proposé à l'utilisateur ; il n'est enregistré qu'après la saisie d'un premier code (activer). */
    public Enrolement enrolement(String login, byte[] secret) {
        String base32 = Totp.base32(secret);
        String uri = "otpauth://totp/" + encoder(EMETTEUR + ":" + login) + "?secret=" + base32 + "&issuer=" + EMETTEUR
                + "&algorithm=SHA1&digits=" + Totp.CHIFFRES + "&period=" + Totp.PERIODE_SECONDES;
        return new Enrolement(base32.replaceAll("(.{4})(?!$)", "$1 "), uri, QrCodeSvg.svg(uri));
    }

    public static byte[] nouveauSecret() {
        return Totp.nouveauSecret();
    }

    @Transactional
    public UtilisateurConnecte activerDeuxFacteurs(String login, byte[] secret, String code) {
        if (secret == null) {
            throw new RegleMetierException("Aucune activation en cours : recommencez l'opération.");
        }
        long pas = Totp.verifier(secret, code, null, horloge.instant());
        if (pas < 0) {
            audit.enregistrerPour(login, ActionsAudit.DEUX_FACTEURS_ACTIVATION, login, "Code incorrect", false);
            throw new RegleMetierException("Code incorrect : vérifiez l'heure du téléphone et saisissez le code affiché.");
        }
        jdbc.sql("""
                        update securite_utilisateur set totp_secret = ?, totp_active_le = now(), totp_dernier_pas = ?,
                            modifie_le = now() where login = ?""")
                .params(chiffrement.chiffrer(secret), pas, login).update();
        audit.enregistrerPour(login, ActionsAudit.DEUX_FACTEURS_ACTIVATION, login, null, true);
        return recharger(login).orElseThrow();
    }

    @Transactional
    public UtilisateurConnecte desactiverDeuxFacteurs(String login, String motDePasse) {
        Compte c = compte(login).orElseThrow(() -> new RegleMetierException("Utilisateur inconnu."));
        if (politique.lire().deuxFacteursObligatoire()) {
            throw new RegleMetierException("La double authentification est obligatoire : elle ne peut pas être désactivée.");
        }
        if (motDePasse == null || !encodeur.matches(motDePasse, c.motDePasse())) {
            audit.enregistrerPour(c.login(), ActionsAudit.DEUX_FACTEURS_DESACTIVATION, c.login(), "Mot de passe incorrect", false);
            throw new RegleMetierException("Le mot de passe est incorrect.");
        }
        effacerDeuxFacteurs(c.login());
        audit.enregistrerPour(c.login(), ActionsAudit.DEUX_FACTEURS_DESACTIVATION, c.login(), null, true);
        return recharger(c.login()).orElseThrow();
    }

    void effacerDeuxFacteurs(String login) {
        jdbc.sql("""
                        update securite_utilisateur set totp_secret = null, totp_active_le = null, totp_dernier_pas = null,
                            modifie_le = now() where login = ?""")
                .param(login).update();
    }

    /** Jeton Spring Security correspondant à l'utilisateur. */
    public static UsernamePasswordAuthenticationToken jeton(UtilisateurConnecte u) {
        List<GrantedAuthority> autorisations = new ArrayList<>();
        if (u.doitChangerMotDePasse()) {
            autorisations.add(new SimpleGrantedAuthority(Habilitations.CHANGEMENT_MDP_REQUIS));
        } else if (u.doitActiverDeuxFacteurs()) {
            autorisations.add(new SimpleGrantedAuthority(Habilitations.ACTIVATION_2FA_REQUISE));
        } else {
            autorisations.add(new SimpleGrantedAuthority(Habilitations.UTILISATEUR));
            u.menus().forEach(m -> autorisations.add(new SimpleGrantedAuthority(Habilitations.menu(m))));
        }
        return UsernamePasswordAuthenticationToken.authenticated(u, null, autorisations);
    }

    private void exigerCompteUtilisable(Compte c) {
        if (c.verrouille()) {
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Compte verrouillé", false);
            throw new ConnexionRefuseeException(messageVerrouillage());
        }
        if (!c.actif()) {
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Compte désactivé", false);
            throw new ConnexionRefuseeException("Compte désactivé. Contactez l'administrateur.");
        }
    }

    private void echec(Compte c, String motif, String message) {
        int max = politique.lire().echecsMax();
        int echecs = c.nbEchecs() + 1;
        boolean verrouiller = echecs >= max;
        jdbc.sql("update securite_utilisateur set nb_echecs = ?, verrouille = ? where login = ?")
                .params(echecs, verrouiller, c.login()).update();
        audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), motif + " (" + echecs + ")", false);
        if (verrouiller) {
            audit.enregistrerPour(c.login(), ActionsAudit.VERROUILLAGE, c.login(), max + " échecs consécutifs", true);
            throw new ConnexionRefuseeException(messageVerrouillage());
        }
        throw new ConnexionRefuseeException(message);
    }

    private String messageVerrouillage() {
        return "Compte verrouillé après " + politique.lire().echecsMax()
                + " tentatives infructueuses. Contactez l'administrateur.";
    }

    /** Connexion aboutie : remise à zéro des échecs, mot de passe expiré à changer. */
    private UtilisateurConnecte reussir(Compte c, Long pasTotp) {
        jdbc.sql("""
                        update securite_utilisateur set nb_echecs = 0, derniere_connexion = now(),
                            totp_dernier_pas = coalesce(?, totp_dernier_pas) where login = ?""")
                .params(pasTotp, c.login()).update();
        LocalDate expiration = expiration(c);
        if (!c.doitChangerMdp() && expiration != null && !LocalDate.now(horloge).isBefore(expiration)) {
            jdbc.sql("update securite_utilisateur set doit_changer_mdp = true where login = ?").param(c.login()).update();
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(), "Mot de passe expiré : changement imposé", true);
        } else {
            audit.enregistrerPour(c.login(), ActionsAudit.CONNEXION, c.login(),
                    pasTotp == null ? null : "Double authentification", true);
        }
        return recharger(c.login()).orElseThrow();
    }

    private LocalDate expiration(Compte c) {
        int jours = politique.lire().expirationJours();
        return jours <= 0 || c.mdpModifieLe() == null ? null
                : c.mdpModifieLe().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().plusDays(jours);
    }

    private Optional<Compte> compte(String login) {
        return jdbc.sql(SELECT_COMPTE).param(login).query(Compte.class).optional();
    }

    private UtilisateurConnecte versUtilisateur(Compte c) {
        Set<String> menus = new LinkedHashSet<>(jdbc.sql("""
                        select pm.menu_code from securite_profil_menu pm join securite_menu m on m.code = pm.menu_code
                        where pm.profil_code = ? order by m.rang""")
                .param(c.profilCode()).query(String.class).list());
        boolean deuxFacteurs = c.totpSecret() != null;
        return new UtilisateurConnecte(c.login(), c.nom(), c.agence(), c.profilCode(), c.libelleProfil(),
                Set.copyOf(menus), c.doitChangerMdp(), deuxFacteurs,
                !deuxFacteurs && politique.lire().deuxFacteursObligatoire(), expiration(c));
    }

    private static String encoder(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
