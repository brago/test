package com.rapmonet.parametrage.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Paramètres de l'application (table parametre_application) : vue d'ensemble par groupe, modification motivée et
 * historisée des réglages qui n'ont pas d'écran dédié, avec contrôle du type et des bornes de chaque valeur. Les
 * paramètres gérés par un autre écran (politique de sécurité, règles de rapprochement, traitements automatiques,
 * comptes de la compense) y sont affichés en lecture, avec le lien vers cet écran.
 */
@Service
public class ParametresApplication {

    public enum Type { TEXTE, ENTIER, HEURE, FORMAT_DATE, LISTE_ENTIERS, BOOLEEN }

    /**
     * Paramètre affiché.
     *
     * @param modifiable  modifiable ici ; sinon geréPar donne l'écran qui le gère
     * @param exemple     aperçu de la valeur (ex. date formatée), si utile
     */
    public record Parametre(String cle, String groupe, String libelle, String description, Type type, Integer min,
                            Integer max, String valeur, boolean modifiable, String gerePar, String lienEcran,
                            String exemple, String modifiePar, OffsetDateTime modifieLe) {
    }

    public record Modification(long id, String cle, String avant, String apres, String motif, String modifiePar,
                               OffsetDateTime modifieLe) {
    }

    /** Définition d'un paramètre modifiable ici. */
    private record Definition(String groupe, String libelle, Type type, Integer min, Integer max, Pattern motif) {
    }

    /** Préfixes gérés par un autre écran : libellé de l'écran et lien. */
    private record Gestion(String prefixe, String groupe, String ecran, String lien) {
    }

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final List<Gestion> GESTIONS = List.of(
            new Gestion("securite.", "Sécurité", "Politique de sécurité", "/securite/politique"),
            new Gestion("rapprochement.gab_lis.", "Rapprochement GAB ↔ LIS", "Règles de rapprochement", "/rapprochement/regles"),
            new Gestion("ejournal.batch.", "Traitements automatiques", "Chargement des journaux GAB", "/ingestion/ejournal"),
            new Gestion("lis.batch.", "Traitements automatiques", "Chargement des fichiers LIS", "/ingestion"),
            new Gestion("conservation.", "Conservation des données", "Conservation des données", "/parametrage/conservation"),
            new Gestion("compense.", "Compense TPE", "Commerçants et comptes de la compense", "/compense/commercants"));

    static {
        DEFINITIONS.put("application.nom", new Definition("Application", "Nom affiché", Type.TEXTE, 1, 40, null));
        DEFINITIONS.put("application.devise", new Definition("Application", "Devise de référence", Type.TEXTE, 3, 3,
                Pattern.compile("[A-Z]{3}")));
        DEFINITIONS.put("ejournal.coupures", new Definition("Journaux GAB", "Valeur des billets de type 1 à 4 (XOF)",
                Type.LISTE_ENTIERS, 0, 100_000, null));
        DEFINITIONS.put("ejournal.ncr.format_date", new Definition("Journaux GAB", "Format de date des tickets NCR",
                Type.FORMAT_DATE, null, null, null));
        DEFINITIONS.put("gab.activite.plage_debut", new Definition("Activité des GAB", "Début de la plage surveillée",
                Type.HEURE, null, null, null));
        DEFINITIONS.put("gab.activite.plage_fin", new Definition("Activité des GAB", "Fin de la plage surveillée",
                Type.HEURE, null, null, null));
        DEFINITIONS.put("gab.activite.inactivite_alerte_minutes", new Definition("Activité des GAB",
                "Inactivité qui signale un GAB (minutes)", Type.ENTIER, 30, 1440, null));
        DEFINITIONS.put("gab.activite.taux_refus_alerte", new Definition("Activité des GAB",
                "Taux de refus qui signale un GAB (%)", Type.ENTIER, 1, 100, null));
        DEFINITIONS.put("gab.activite.tentatives_min", new Definition("Activité des GAB",
                "Tentatives minimales pour calculer le taux de refus", Type.ENTIER, 1, 1000, null));
        DEFINITIONS.put("reclamation.delai_jours", new Definition("Réclamations porteurs", "Délai de traitement (jours)",
                Type.ENTIER, 1, 120, null));
        DEFINITIONS.put("chargeback.delai_jours", new Definition("Chargebacks", "Délai de représentation (jours)",
                Type.ENTIER, 1, 365, null));
    }

    private final JdbcClient jdbc;
    private final JournalAudit audit;

    ParametresApplication(JdbcClient jdbc, JournalAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private record Ligne(String cle, String valeur, String description, String modifiePar, OffsetDateTime modifieLe) {
    }

    @Transactional(readOnly = true)
    public List<Parametre> lister() {
        List<Parametre> l = new ArrayList<>();
        for (Ligne p : jdbc.sql("select cle, valeur, description, modifie_par, modifie_le from parametre_application order by cle")
                .query(Ligne.class).list()) {
            l.add(versParametre(p));
        }
        // Ordre : groupes modifiables d'abord (ordre du catalogue), puis les autres.
        List<String> ordre = new ArrayList<>(DEFINITIONS.keySet());
        l.sort((a, b) -> {
            int ia = ordre.indexOf(a.cle()), ib = ordre.indexOf(b.cle());
            if (ia >= 0 || ib >= 0) {
                return Integer.compare(ia < 0 ? Integer.MAX_VALUE : ia, ib < 0 ? Integer.MAX_VALUE : ib);
            }
            return (a.groupe() + a.cle()).compareTo(b.groupe() + b.cle());
        });
        return l;
    }

    @Transactional
    public Parametre modifier(String cle, String valeur, String motif, String utilisateur) {
        Definition d = DEFINITIONS.get(cle);
        if (d == null) {
            Gestion g = gestion(cle);
            throw new RegleMetierException(g != null ? "Ce paramètre se modifie dans l'écran « " + g.ecran() + " »."
                    : "Ce paramètre n'est pas modifiable à l'écran.");
        }
        Ligne avant = jdbc.sql("select cle, valeur, description, modifie_par, modifie_le from parametre_application where cle = ?")
                .param(cle).query(Ligne.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Paramètre inconnu : " + cle));
        String texte = motif == null ? "" : motif.strip();
        if (texte.length() < 5 || texte.length() > 500) {
            throw new RegleMetierException("Indiquez le motif de la modification (5 à 500 caractères).");
        }
        String v = normaliser(d, valeur);
        controlerCoherence(cle, v);
        if (v.equals(avant.valeur())) {
            throw new RegleMetierException("La valeur est inchangée.");
        }
        jdbc.sql("update parametre_application set valeur = ?, modifie_par = ?, modifie_le = now() where cle = ?")
                .params(v, utilisateur, cle).update();
        jdbc.sql("insert into parametre_historique (cle, avant, apres, motif, modifie_par) values (?, ?, ?, ?, ?)")
                .params(cle, avant.valeur(), v, texte, utilisateur).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_APPLICATION, cle,
                avant.valeur() + " → " + v + " : " + texte, true);
        return lister().stream().filter(p -> p.cle().equals(cle)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Modification> historique() {
        return jdbc.sql("select id, cle, avant, apres, motif, modifie_par, modifie_le from parametre_historique order by id desc limit 200")
                .query(Modification.class).list();
    }

    // ------------------------------------------------------------------ contrôles

    static String normaliser(Definition d, String valeur) {
        String v = valeur == null ? "" : valeur.strip();
        switch (d.type()) {
            case TEXTE -> {
                if (v.length() < d.min() || v.length() > d.max() || (d.motif() != null && !d.motif().matcher(v).matches())) {
                    throw new RegleMetierException(d.libelle() + " : " + (d.motif() != null ? "valeur attendue de "
                            + d.min() + " lettres majuscules (ex. XOF)." : "de " + d.min() + " à " + d.max() + " caractères."));
                }
                return v;
            }
            case ENTIER -> {
                return String.valueOf(entier(v, d));
            }
            case HEURE -> {
                if (!v.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
                    throw new RegleMetierException(d.libelle() + " : heure attendue au format HH:mm (ex. 07:00).");
                }
                return v;
            }
            case FORMAT_DATE -> {
                try {
                    DateTimeFormatter f = DateTimeFormatter.ofPattern(v, Locale.FRANCE);
                    LocalDate exemple = LocalDate.of(2026, 5, 22);
                    if (!LocalDate.parse(f.format(exemple), f).equals(exemple) || v.contains("H") || v.contains("m")) {
                        throw new IllegalArgumentException();
                    }
                } catch (RuntimeException e) {
                    throw new RegleMetierException(d.libelle() + " : format de date invalide (ex. yy/MM/dd ou dd/MM/yy).");
                }
                return v;
            }
            case LISTE_ENTIERS -> {
                String[] p = v.split("\\s*[,;]\\s*");
                if (p.length != 4) {
                    throw new RegleMetierException(d.libelle() + " : 4 valeurs attendues, séparées par des virgules "
                            + "(0 pour un type non chargé), ex. 10000,5000,2000,0.");
                }
                List<String> l = new ArrayList<>();
                for (String x : p) {
                    l.add(String.valueOf(entier(x, d)));
                }
                if (l.stream().allMatch("0"::equals)) {
                    throw new RegleMetierException(d.libelle() + " : au moins un type de billet doit avoir une valeur.");
                }
                return String.join(",", l);
            }
            case BOOLEEN -> {
                if (!v.equals("true") && !v.equals("false")) {
                    throw new RegleMetierException(d.libelle() + " : true ou false.");
                }
                return v;
            }
            default -> throw new IllegalStateException();
        }
    }

    /** Contrôles entre paramètres : la plage surveillée doit avoir un début avant sa fin. */
    private void controlerCoherence(String cle, String v) {
        if (cle.equals("gab.activite.plage_debut") || cle.equals("gab.activite.plage_fin")) {
            Map<String, String> m = new HashMap<>();
            jdbc.sql("select cle, valeur from parametre_application where cle in ('gab.activite.plage_debut', 'gab.activite.plage_fin')")
                    .query(rs -> {
                        m.put(rs.getString(1), rs.getString(2));
                    });
            m.put(cle, v);
            LocalTime debut = LocalTime.parse(m.getOrDefault("gab.activite.plage_debut", "07:00"));
            LocalTime fin = LocalTime.parse(m.getOrDefault("gab.activite.plage_fin", "21:00"));
            if (!debut.isBefore(fin)) {
                throw new RegleMetierException("Le début de la plage surveillée doit précéder sa fin ("
                        + debut + " – " + fin + ").");
            }
        }
    }

    private static int entier(String v, Definition d) {
        try {
            int n = Integer.parseInt(v.strip());
            if (n < d.min() || n > d.max()) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new RegleMetierException(d.libelle() + " : nombre entier attendu entre " + d.min() + " et " + d.max() + ".");
        }
    }

    private static Gestion gestion(String cle) {
        return GESTIONS.stream().filter(g -> cle.startsWith(g.prefixe())).findFirst().orElse(null);
    }

    private static Parametre versParametre(Ligne p) {
        Definition d = DEFINITIONS.get(p.cle());
        if (d != null) {
            return new Parametre(p.cle(), d.groupe(), d.libelle(), p.description(), d.type(), d.min(), d.max(), p.valeur(),
                    true, null, null, exemple(d.type(), p.valeur()), p.modifiePar(), p.modifieLe());
        }
        Gestion g = gestion(p.cle());
        return new Parametre(p.cle(), g != null ? g.groupe() : "Autres", p.cle(), p.description(), Type.TEXTE, null, null,
                p.valeur(), false, g != null ? g.ecran() : null, g != null ? g.lien() : null, null, p.modifiePar(), p.modifieLe());
    }

    private static String exemple(Type t, String valeur) {
        if (t != Type.FORMAT_DATE) {
            return null;
        }
        try {
            return "22/05/2026 s'écrit « " + DateTimeFormatter.ofPattern(valeur, Locale.FRANCE).format(LocalDate.of(2026, 5, 22)) + " »";
        } catch (RuntimeException e) {
            return null;
        }
    }
}
