package com.rapmonet.securite.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.securite.ActionsAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Politique de mot de passe et de connexion, paramétrable à l'écran (table parametre_application). */
@Service
public class PolitiqueSecurite {

    /**
     * Paramètres de la politique.
     *
     * @param expirationJours        durée de validité du mot de passe, 0 pour aucune expiration
     * @param historique             nombre de mots de passe précédents non réutilisables
     * @param echecsMax              échecs consécutifs avant verrouillage
     * @param deuxFacteursObligatoire double authentification imposée à tous les utilisateurs
     */
    public record Politique(int longueurMin, boolean majuscule, boolean minuscule, boolean chiffre, boolean special,
                            int expirationJours, int historique, int echecsMax, boolean deuxFacteursObligatoire) {
    }

    /** Règle de composition affichable, avec son expression de contrôle côté navigateur. */
    public record Regle(String libelle, String motif) {
    }

    private static final String PREFIXE = "securite.";
    private static final Map<String, String> CLES = new LinkedHashMap<>();

    static {
        CLES.put("longueurMin", "mdp.longueur_min");
        CLES.put("majuscule", "mdp.majuscule");
        CLES.put("minuscule", "mdp.minuscule");
        CLES.put("chiffre", "mdp.chiffre");
        CLES.put("special", "mdp.special");
        CLES.put("expirationJours", "mdp.expiration_jours");
        CLES.put("historique", "mdp.historique");
        CLES.put("echecsMax", "connexion.echecs_max");
        CLES.put("deuxFacteursObligatoire", "deux_facteurs.obligatoire");
    }

    private final JdbcClient jdbc;
    private final AuditService audit;

    PolitiqueSecurite(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Politique lire() {
        Map<String, String> v = new LinkedHashMap<>();
        jdbc.sql("select cle, valeur from parametre_application where cle like 'securite.%'")
                .query((rs, n) -> v.put(rs.getString(1).substring(PREFIXE.length()), rs.getString(2))).list();
        return new Politique(entier(v, "longueurMin", 10), booleen(v, "majuscule", true), booleen(v, "minuscule", true),
                booleen(v, "chiffre", true), booleen(v, "special", false), entier(v, "expirationJours", 90),
                entier(v, "historique", 5), entier(v, "echecsMax", 3), booleen(v, "deuxFacteursObligatoire", false));
    }

    @Transactional
    public Politique modifier(Politique p) {
        borner(p.longueurMin(), 8, 64, "La longueur minimale");
        borner(p.expirationJours(), 0, 730, "La durée de validité");
        borner(p.historique(), 0, 24, "L'historique");
        borner(p.echecsMax(), 3, 10, "Le nombre d'échecs avant verrouillage");
        if (!p.majuscule() && !p.minuscule()) {
            throw new RegleMetierException("Exigez au moins les majuscules ou les minuscules.");
        }
        Politique avant = lire();
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("longueurMin", p.longueurMin());
        valeurs.put("majuscule", p.majuscule());
        valeurs.put("minuscule", p.minuscule());
        valeurs.put("chiffre", p.chiffre());
        valeurs.put("special", p.special());
        valeurs.put("expirationJours", p.expirationJours());
        valeurs.put("historique", p.historique());
        valeurs.put("echecsMax", p.echecsMax());
        valeurs.put("deuxFacteursObligatoire", p.deuxFacteursObligatoire());
        valeurs.forEach((champ, valeur) -> jdbc.sql("""
                        insert into parametre_application (cle, valeur) values (?, ?)
                        on conflict (cle) do update set valeur = excluded.valeur, modifie_le = now()""")
                .params(PREFIXE + CLES.get(champ), String.valueOf(valeur)).update());
        audit.enregistrer(ActionsAudit.POLITIQUE_SECURITE, "Politique de sécurité", avant + " → " + p);
        return lire();
    }

    /** Règles de composition de la politique en vigueur. */
    @Transactional(readOnly = true)
    public List<Regle> regles() {
        return regles(lire());
    }

    static List<Regle> regles(Politique p) {
        List<Regle> r = new ArrayList<>();
        r.add(new Regle("Au moins " + p.longueurMin() + " caractères", "^[\\s\\S]{" + p.longueurMin() + ",}$"));
        if (p.majuscule()) {
            r.add(new Regle("Au moins une majuscule", "\\p{Lu}"));
        }
        if (p.minuscule()) {
            r.add(new Regle("Au moins une minuscule", "\\p{Ll}"));
        }
        if (p.chiffre()) {
            r.add(new Regle("Au moins un chiffre", "\\d"));
        }
        if (p.special()) {
            r.add(new Regle("Au moins un caractère spécial (ex. ! ? @ # %)", "[^\\p{L}\\d\\s]"));
        }
        return r;
    }

    /** Vérifie la composition du mot de passe ; l'historique est contrôlé par l'appelant. */
    void verifierComposition(String login, String motDePasse) {
        Politique p = lire();
        if (motDePasse == null || motDePasse.length() < p.longueurMin()) {
            throw new RegleMetierException("Le mot de passe doit contenir au moins " + p.longueurMin() + " caractères.");
        }
        List<String> manque = new ArrayList<>();
        if (p.majuscule() && motDePasse.chars().noneMatch(Character::isUpperCase)) {
            manque.add("une majuscule");
        }
        if (p.minuscule() && motDePasse.chars().noneMatch(Character::isLowerCase)) {
            manque.add("une minuscule");
        }
        if (p.chiffre() && motDePasse.chars().noneMatch(Character::isDigit)) {
            manque.add("un chiffre");
        }
        if (p.special() && motDePasse.chars().allMatch(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c))) {
            manque.add("un caractère spécial");
        }
        if (!manque.isEmpty()) {
            throw new RegleMetierException("Le mot de passe doit contenir au moins " + String.join(", ", manque) + ".");
        }
        if (motDePasse.toLowerCase().contains(login.toLowerCase())) {
            throw new RegleMetierException("Le mot de passe ne doit pas contenir l'identifiant.");
        }
    }

    private static void borner(int valeur, int min, int max, String libelle) {
        if (valeur < min || valeur > max) {
            throw new RegleMetierException(libelle + " : valeur attendue entre " + min + " et " + max + ".");
        }
    }

    private static int entier(Map<String, String> v, String champ, int defaut) {
        String s = v.get(CLES.get(champ));
        try {
            return s == null ? defaut : Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    private static boolean booleen(Map<String, String> v, String champ, boolean defaut) {
        String s = v.get(CLES.get(champ));
        return s == null ? defaut : Boolean.parseBoolean(s.strip());
    }
}
