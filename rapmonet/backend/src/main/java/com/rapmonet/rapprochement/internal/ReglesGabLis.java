package com.rapmonet.rapprochement.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Règles du rapprochement GAB ↔ LIS, modifiables à l'écran (table parametre_application) avec historique motivé. */
@Service
public class ReglesGabLis {

    /** Champs de l'avis LIS comparables à la référence imprimée sur le ticket. */
    public enum ChampReference {
        AUTORISATION("code d'autorisation"), BORDEREAU("numéro de bordereau"), RRN("fin du RRN");

        public final String libelle;

        ChampReference(String libelle) {
            this.libelle = libelle;
        }
    }

    /**
     * Règles d'appariement d'un retrait du journal et d'un avis LIS.
     *
     * @param toleranceMinutes      écart horaire maximal entre ticket et avis
     * @param delaiLisJours         jours laissés au GIM pour remonter un retrait
     * @param memeCarte             exiger la même carte (6 premiers et 4 derniers chiffres)
     * @param toleranceMontant      écart de montant toléré, en XOF
     * @param reference             rapprocher aussi sur la référence du ticket, quelle que soit l'heure
     * @param champsReference       champs de l'avis comparés à la référence
     * @param fenetreReferenceJours écart maximal en jours pour un rapprochement sur la référence
     */
    public record Regles(int toleranceMinutes, int delaiLisJours, boolean memeCarte, long toleranceMontant,
                         boolean reference, Set<ChampReference> champsReference, int fenetreReferenceJours) {

        String lisible() {
            return "tolérance " + toleranceMinutes + " min ; délai LIS " + delaiLisJours + " j ; "
                    + (memeCarte ? "même carte" : "carte non comparée") + " ; écart de montant " + toleranceMontant
                    + " XOF ; référence " + (reference ? champsReference.stream().map(c -> c.libelle)
                    .collect(Collectors.joining(", ")) + " à " + fenetreReferenceJours + " j près" : "non utilisée");
        }
    }

    public record Modification(OffsetDateTime modifieLe, String modifiePar, String motif, String avant, String apres) {
    }

    static final String RAPPROCHEMENT = "GAB_LIS";
    private static final String PREFIXE = "rapprochement.gab_lis.";

    private final JdbcClient jdbc;
    private final JournalAudit audit;

    ReglesGabLis(JdbcClient jdbc, JournalAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Regles lire() {
        Map<String, String> v = new HashMap<>();
        jdbc.sql("select cle, valeur from parametre_application where cle like 'rapprochement.gab_lis.%'")
                .query((rs, n) -> v.put(rs.getString(1).substring(PREFIXE.length()), rs.getString(2).strip())).list();
        Set<ChampReference> champs = EnumSet.noneOf(ChampReference.class);
        for (String c : v.getOrDefault("reference_champs", "AUTORISATION,BORDEREAU,RRN").split(",")) {
            try {
                champs.add(ChampReference.valueOf(c.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // champ inconnu ignoré
            }
        }
        return new Regles(entier(v, "tolerance_minutes", 120), entier(v, "delai_lis_jours", 3),
                !"false".equalsIgnoreCase(v.get("meme_carte")), entier(v, "tolerance_montant", 0),
                !"false".equalsIgnoreCase(v.get("reference_active")), champs, entier(v, "reference_fenetre_jours", 1));
    }

    /** Contrôle des bornes ; renvoie les règles normalisées. */
    public Regles valider(Regles r) {
        if (r == null) {
            throw new RegleMetierException("Règles absentes.");
        }
        borner(r.toleranceMinutes(), 0, 1440, "La tolérance horaire (minutes)");
        borner(r.delaiLisJours(), 0, 30, "Le délai de remontée des LIS (jours)");
        borner(r.toleranceMontant(), 0, 100_000, "L'écart de montant toléré (XOF)");
        borner(r.fenetreReferenceJours(), 0, 7, "La fenêtre de la référence (jours)");
        Set<ChampReference> champs = r.champsReference() == null || r.champsReference().isEmpty()
                ? EnumSet.noneOf(ChampReference.class) : EnumSet.copyOf(r.champsReference());
        if (r.reference() && champs.isEmpty()) {
            throw new RegleMetierException("Choisissez au moins un champ LIS à comparer à la référence du ticket.");
        }
        if (!r.memeCarte() && !r.reference() && r.toleranceMinutes() > 30) {
            throw new RegleMetierException("Sans comparaison de la carte ni de la référence, la tolérance horaire est "
                    + "limitée à 30 minutes (risque d'apparier deux retraits différents de même montant).");
        }
        return new Regles(r.toleranceMinutes(), r.delaiLisJours(), r.memeCarte(), r.toleranceMontant(), r.reference(),
                champs, r.fenetreReferenceJours());
    }

    @Transactional
    public Regles modifier(Regles proposees, String motif, String utilisateur) {
        Regles r = valider(proposees);
        String texte = motif == null ? "" : motif.strip();
        if (texte.length() < 5) {
            throw new RegleMetierException("Indiquez le motif de la modification (5 caractères au moins).");
        }
        if (texte.length() > 500) {
            throw new RegleMetierException("Le motif est limité à 500 caractères.");
        }
        Regles avant = lire();
        if (avant.equals(r)) {
            throw new RegleMetierException("Aucune règle n'a été modifiée.");
        }
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("tolerance_minutes", r.toleranceMinutes());
        valeurs.put("delai_lis_jours", r.delaiLisJours());
        valeurs.put("meme_carte", r.memeCarte());
        valeurs.put("tolerance_montant", r.toleranceMontant());
        valeurs.put("reference_active", r.reference());
        valeurs.put("reference_champs", r.champsReference().stream().map(Enum::name).collect(Collectors.joining(",")));
        valeurs.put("reference_fenetre_jours", r.fenetreReferenceJours());
        valeurs.forEach((cle, valeur) -> jdbc.sql("""
                        insert into parametre_application (cle, valeur) values (?, ?)
                        on conflict (cle) do update set valeur = excluded.valeur, modifie_le = now()""")
                .params(PREFIXE + cle, String.valueOf(valeur)).update());
        jdbc.sql("""
                        insert into rapprochement_regle_historique (rapprochement, modifie_par, motif, avant, apres)
                        values (?, ?, ?, ?, ?)""")
                .params(RAPPROCHEMENT, utilisateur, texte, avant.lisible(), r.lisible()).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.RAPPROCHEMENT_REGLES, "Règles GAB / LIS",
                texte + " — " + r.lisible(), true);
        return lire();
    }

    @Transactional(readOnly = true)
    public List<Modification> historique() {
        return new ArrayList<>(jdbc.sql("""
                        select modifie_le, modifie_par, motif, avant, apres from rapprochement_regle_historique
                        where rapprochement = ? order by id desc limit 100""")
                .param(RAPPROCHEMENT).query(Modification.class).list());
    }

    private static void borner(long valeur, long min, long max, String libelle) {
        if (valeur < min || valeur > max) {
            throw new RegleMetierException(libelle + " : valeur attendue entre " + min + " et " + max + ".");
        }
    }

    private static int entier(Map<String, String> v, String cle, int defaut) {
        try {
            return v.containsKey(cle) ? Integer.parseInt(v.get(cle)) : defaut;
        } catch (NumberFormatException e) {
            return defaut;
        }
    }
}
