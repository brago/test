package com.rapmonet.parametrage.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.parametrage.Pad;
import com.rapmonet.parametrage.TypeDonnee;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Modification du paramétrage d'un format positionnel (LIS du GIM) à l'écran : dictionnaire des champs, structure
 * des TCR, TC. Chaque modification est motivée et historisée (fmt_modification) ; elle s'applique aux fichiers
 * intégrés ensuite, les fichiers déjà intégrés ne sont pas relus.
 *
 * <p>Garde-fous : un TCR ne dépasse pas la longueur disponible de l'enregistrement ; un champ protégé (lu par les
 * traitements) n'est jamais retiré d'un TCR ni rendu non stocké ; un champ confidentiel (numéro de carte) le reste.
 */
@Service
public class EditionFormats {

    /**
     * Champ du dictionnaire.
     *
     * @param protege       lu par les traitements de l'application
     * @param utilisations  TC et TCR qui le contiennent, ex. 49/1
     */
    public record Champ(String code, String libelle, String description, int taille, Pad pad, int decimales,
                        TypeDonnee type, boolean specifique, boolean confidentiel, boolean protege,
                        List<String> utilisations) {
    }

    /** Valeurs modifiables d'un champ (le code ne change jamais : il est référencé par les données intégrées). */
    public record SaisieChamp(String code, String libelle, String description, int taille, Pad pad, int decimales,
                              TypeDonnee type, boolean specifique, boolean confidentiel) {
    }

    public record SaisieTcr(String code, String libelle) {
    }

    public record SaisieTc(String code, String libelle, String description, List<SaisieTcr> tcrs,
                           List<String> sousFichiers) {
    }

    public record Modification(long id, String objet, String action, String motif, String avant, String apres,
                               String modifiePar, OffsetDateTime modifieLe) {
    }

    private record Fichier(long id, String famille, int longueurEnregistrement, int positionDonnees) {
        int capacite() {
            return longueurEnregistrement - positionDonnees + 1;
        }
    }

    private record LigneChamp(long id, String code, String libelle, String description, int taille, String pad,
                              int decimales, String typeDonnee, boolean specifique, boolean confidentiel,
                              boolean protege) {
        String lisible() {
            return libelle + " ; taille " + taille + " ; " + typeDonnee + (decimales > 0 ? " (" + decimales + " déc.)" : "")
                    + " ; complément " + pad + (specifique ? " ; stocké" : " ; non stocké")
                    + (confidentiel ? " ; confidentiel" : "");
        }
    }

    private static final Pattern CODE_CHAMP = Pattern.compile("[A-Z][A-Z0-9_]{2,59}");
    private static final Pattern CODE_TC = Pattern.compile("[0-9A-Z]{1,10}");

    private final JdbcClient jdbc;
    private final JournalAudit audit;

    EditionFormats(JdbcClient jdbc, JournalAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ consultation

    /** Champs utilisés par les TC du format, puis champs créés et pas encore placés. */
    @Transactional(readOnly = true)
    public List<Champ> champs(String format) {
        Fichier f = fichier(format);
        record Utilisation(long attributId, String tc, String tcr) {
        }
        List<Utilisation> utilisations = jdbc.sql("""
                        select ta.attribut_id, tc.code as tc, tcr.code as tcr
                        from fmt_tcr_attribut ta join fmt_tcr tcr on tcr.id = ta.tcr_id join fmt_tc tc on tc.id = tcr.tc_id
                        where tc.famille = ? order by tc.code, tcr.rang""")
                .param(f.famille()).query(Utilisation.class).list();
        String prefixe = f.famille() + "_";
        List<Champ> resultat = new ArrayList<>();
        for (LigneChamp c : jdbc.sql("select * from fmt_attribut order by code").query(LigneChamp.class).list()) {
            List<String> u = utilisations.stream().filter(x -> x.attributId() == c.id())
                    .map(x -> x.tc() + "/" + x.tcr()).distinct().toList();
            if (!u.isEmpty() || c.code().startsWith(prefixe)) {
                resultat.add(versChamp(c, u));
            }
        }
        return resultat;
    }

    @Transactional(readOnly = true)
    public List<Modification> historique(String format) {
        fichier(format);
        return jdbc.sql("""
                        select id, objet, action, motif, avant, apres, modifie_par, modifie_le from fmt_modification
                        where format_code = ? order by id desc limit 200""")
                .param(format).query(Modification.class).list();
    }

    // ------------------------------------------------------------------ champs

    @Transactional
    public Champ modifierChamp(String format, String code, SaisieChamp s, String motif, String utilisateur) {
        Fichier f = fichier(format);
        String texte = exigerMotif(motif);
        LigneChamp avant = ligneChamp(code);
        controlerChamp(s);
        if (avant.confidentiel() && !s.confidentiel()) {
            throw new RegleMetierException("Un champ confidentiel (numéro de carte) reste masqué avant stockage : "
                    + "cette option ne peut pas être retirée.");
        }
        if (avant.protege() && !s.specifique()) {
            throw new RegleMetierException("Ce champ est lu par les traitements de l'application : il doit rester stocké.");
        }
        if (avant.protege() && (TypeDonnee.valueOf(avant.typeDonnee()) != s.type() || avant.decimales() != s.decimales())) {
            throw new RegleMetierException("Ce champ est lu par les traitements de l'application : son type et ses "
                    + "décimales ne peuvent pas changer (seuls le libellé, la taille et le complément le peuvent).");
        }
        if (s.taille() != avant.taille()) {
            // Tous les TCR qui contiennent le champ doivent encore tenir dans l'enregistrement.
            List<String> trop = jdbc.sql("""
                            select tc.code || '/' || tcr.code from fmt_tcr tcr join fmt_tc tc on tc.id = tcr.tc_id
                            join fmt_tcr_attribut ta on ta.tcr_id = tcr.id join fmt_attribut a on a.id = ta.attribut_id
                            where tc.famille = ? and tcr.id in (select tcr_id from fmt_tcr_attribut where attribut_id = ?)
                            group by tc.code, tcr.code
                            having sum(case when a.id = ? then ? else a.taille end) > ?""")
                    .params(f.famille(), avant.id(), avant.id(), s.taille(), f.capacite()).query(String.class).list();
            if (!trop.isEmpty()) {
                throw new RegleMetierException("Avec une taille de " + s.taille() + ", les TCR " + String.join(", ", trop)
                        + " dépasseraient " + f.capacite() + " caractères : réduisez d'abord une zone de réserve (filler).");
            }
        }
        jdbc.sql("""
                        update fmt_attribut set libelle = ?, description = ?, taille = ?, pad = ?, decimales = ?,
                            type_donnee = ?, specifique = ?, confidentiel = ? where id = ?""")
                .params(s.libelle().strip(), vide(s.description()), s.taille(), s.pad().name(), s.decimales(),
                        s.type().name(), s.specifique(), s.confidentiel(), avant.id())
                .update();
        LigneChamp apres = ligneChamp(code);
        if (apres.lisible().equals(avant.lisible()) && Objects.equals(apres.description(), avant.description())) {
            throw new RegleMetierException("Aucune modification.");
        }
        historiser(format, "Champ " + code, "CHAMP_MODIFIE", texte, avant.lisible(), apres.lisible(), utilisateur);
        return champs(format).stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    @Transactional
    public Champ creerChamp(String format, SaisieChamp s, String motif, String utilisateur) {
        Fichier f = fichier(format);
        String texte = exigerMotif(motif);
        String code = s.code() == null ? "" : s.code().strip().toUpperCase();
        if (!CODE_CHAMP.matcher(code).matches() || !code.startsWith(f.famille() + "_")) {
            throw new RegleMetierException("Code attendu : " + f.famille() + "_ suivi de lettres majuscules, chiffres ou _ "
                    + "(ex. " + f.famille() + "_NOUVEAU_CHAMP).");
        }
        controlerChamp(s);
        if (jdbc.sql("select count(*) from fmt_attribut where code = ?").param(code).query(Integer.class).single() > 0) {
            throw new RegleMetierException("Le champ " + code + " existe déjà.");
        }
        jdbc.sql("""
                        insert into fmt_attribut (code, libelle, description, taille, pad, decimales, type_donnee,
                            specifique, confidentiel) values (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(code, s.libelle().strip(), vide(s.description()), s.taille(), s.pad().name(), s.decimales(),
                        s.type().name(), s.specifique(), s.confidentiel())
                .update();
        historiser(format, "Champ " + code, "CHAMP_CREE", texte, null, ligneChamp(code).lisible(), utilisateur);
        return champs(format).stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ structure des TCR

    /** Remplace la liste ordonnée des champs d'un TCR ; les positions se déduisent des tailles. */
    @Transactional
    public void modifierStructure(String format, String tc, String tcr, List<String> codes, String motif,
                                  String utilisateur) {
        Fichier f = fichier(format);
        String texte = exigerMotif(motif);
        long tcrId = jdbc.sql("""
                        select tcr.id from fmt_tcr tcr join fmt_tc t on t.id = tcr.tc_id
                        where t.famille = ? and t.code = ? and tcr.code = ?""")
                .params(f.famille(), tc, tcr).query(Long.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("TC " + tc + " / TCR " + tcr + " inconnu."));
        if (codes == null || codes.isEmpty()) {
            throw new RegleMetierException("Un TCR doit contenir au moins un champ.");
        }
        List<LigneChamp> nouveaux = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        for (String c : codes) {
            if (!vus.add(c)) {
                throw new RegleMetierException("Le champ " + c + " figure deux fois dans le TCR.");
            }
            nouveaux.add(ligneChamp(c));
        }
        int total = nouveaux.stream().mapToInt(LigneChamp::taille).sum();
        if (total > f.capacite()) {
            throw new RegleMetierException("Les champs occupent " + total + " caractères : au plus " + f.capacite()
                    + " (positions " + f.positionDonnees() + " à " + f.longueurEnregistrement() + ").");
        }
        List<LigneChamp> anciens = jdbc.sql("""
                        select a.* from fmt_tcr_attribut ta join fmt_attribut a on a.id = ta.attribut_id
                        where ta.tcr_id = ? order by ta.rang""")
                .param(tcrId).query(LigneChamp.class).list();
        List<String> retires = anciens.stream().filter(a -> a.protege() && !vus.contains(a.code()))
                .map(LigneChamp::code).toList();
        if (!retires.isEmpty()) {
            throw new RegleMetierException("Champs lus par les traitements, à conserver dans ce TCR : "
                    + String.join(", ", retires) + ".");
        }
        String avant = structure(anciens, f.positionDonnees());
        String apres = structure(nouveaux, f.positionDonnees());
        if (avant.equals(apres)) {
            throw new RegleMetierException("Aucune modification.");
        }
        jdbc.sql("delete from fmt_tcr_attribut where tcr_id = ?").param(tcrId).update();
        for (int i = 0; i < nouveaux.size(); i++) {
            jdbc.sql("insert into fmt_tcr_attribut (tcr_id, attribut_id, rang) values (?, ?, ?)")
                    .params(tcrId, nouveaux.get(i).id(), i + 1).update();
        }
        historiser(format, "TC " + tc + " / TCR " + tcr, "STRUCTURE_TCR", texte, avant, apres, utilisateur);
    }

    // ------------------------------------------------------------------ TC

    @Transactional
    public void modifierTc(String format, String tc, String libelle, String description, String motif, String utilisateur) {
        Fichier f = fichier(format);
        String texte = exigerMotif(motif);
        record Ligne(String libelle, String description) {
        }
        Ligne avant = jdbc.sql("select libelle, description from fmt_tc where famille = ? and code = ?")
                .params(f.famille(), tc).query(Ligne.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("TC inconnu : " + tc));
        exigerLibelle(libelle);
        if (avant.libelle().equals(libelle.strip()) && Objects.equals(avant.description(), vide(description))) {
            throw new RegleMetierException("Aucune modification.");
        }
        jdbc.sql("update fmt_tc set libelle = ?, description = ? where famille = ? and code = ?")
                .params(libelle.strip(), vide(description), f.famille(), tc).update();
        historiser(format, "TC " + tc, "TC_MODIFIE", texte, avant.libelle() + " — " + Objects.toString(avant.description(), ""),
                libelle.strip() + " — " + Objects.toString(vide(description), ""), utilisateur);
    }

    /**
     * Nouveau TC annoncé par le GIM : ses TCR sont créés avec une zone de réserve couvrant tout l'enregistrement,
     * à détailler ensuite champ par champ.
     */
    @Transactional
    public void creerTc(String format, SaisieTc s, String motif, String utilisateur) {
        Fichier f = fichier(format);
        String texte = exigerMotif(motif);
        String code = s.code() == null ? "" : s.code().strip().toUpperCase();
        if (!CODE_TC.matcher(code).matches()) {
            throw new RegleMetierException("Code TC attendu : chiffres ou lettres (ex. 52).");
        }
        exigerLibelle(s.libelle());
        if (jdbc.sql("select count(*) from fmt_tc where famille = ? and code = ?").params(f.famille(), code)
                .query(Integer.class).single() > 0) {
            throw new RegleMetierException("Le TC " + code + " existe déjà.");
        }
        List<SaisieTcr> tcrs = s.tcrs() == null || s.tcrs().isEmpty() ? List.of(new SaisieTcr("0", "TCR 0")) : s.tcrs();
        Set<String> codesTcr = new LinkedHashSet<>();
        for (SaisieTcr t : tcrs) {
            if (t.code() == null || !t.code().strip().matches("\\d{1,2}") || !codesTcr.add(t.code().strip())) {
                throw new RegleMetierException("Numéros de TCR attendus : 0, 1, 2… sans doublon.");
            }
        }
        long tcId = jdbc.sql("""
                        insert into fmt_tc (famille, code, libelle, description, longueur, nb_tcr) values (?, ?, ?, ?, ?, ?)
                        returning id""")
                .params(f.famille(), code, s.libelle().strip(), vide(s.description()), f.longueurEnregistrement(),
                        tcrs.size()).query(Long.class).single();
        String reserve = reserve(f);
        int rang = 1;
        for (SaisieTcr t : tcrs) {
            long tcrId = jdbc.sql("insert into fmt_tcr (tc_id, code, libelle, rang) values (?, ?, ?, ?) returning id")
                    .params(tcId, t.code().strip(), t.libelle() == null || t.libelle().isBlank()
                            ? "TCR " + t.code().strip() : t.libelle().strip(), rang++).query(Long.class).single();
            jdbc.sql("insert into fmt_tcr_attribut (tcr_id, attribut_id, rang) values (?, (select id from fmt_attribut where code = ?), 1)")
                    .params(tcrId, reserve).update();
        }
        List<String> sousFichiers = s.sousFichiers() == null ? List.of() : s.sousFichiers();
        for (String sf : sousFichiers) {
            int n = jdbc.sql("""
                            insert into fmt_sous_fichier_tc (sous_fichier_id, tc_id, rang)
                            select id, ?, (select coalesce(max(rang), 0) + 1 from fmt_sous_fichier_tc st where st.sous_fichier_id = s.id)
                            from fmt_sous_fichier s where s.famille = ? and s.code = ?""")
                    .params(tcId, f.famille(), sf).update();
            if (n == 0) {
                throw new RegleMetierException("Sous-fichier inconnu : " + sf);
            }
        }
        historiser(format, "TC " + code, "TC_CREE", texte, null, s.libelle().strip() + " ; TCR " + String.join(", ", codesTcr)
                + (sousFichiers.isEmpty() ? "" : " ; sous-fichiers " + String.join(", ", sousFichiers)), utilisateur);
    }

    // ------------------------------------------------------------------ outils

    /** Zone de réserve couvrant tout l'enregistrement, créée au besoin. */
    private String reserve(Fichier f) {
        String code = f.famille() + "_RESERVE_" + f.capacite();
        if (jdbc.sql("select count(*) from fmt_attribut where code = ?").param(code).query(Integer.class).single() == 0) {
            jdbc.sql("""
                            insert into fmt_attribut (code, libelle, taille, pad, decimales, type_donnee, specifique, description)
                            values (?, ?, ?, 'ESPACE_DROITE', 0, 'ALPHA', false, ?)""")
                    .params(code, "Zone non détaillée (" + f.capacite() + " car.)", f.capacite(),
                            "Réserve d'un TC créé à l'écran, à remplacer par ses champs.").update();
        }
        return code;
    }

    private void controlerChamp(SaisieChamp s) {
        exigerLibelle(s.libelle());
        if (s.taille() < 1 || s.taille() > 247) {
            throw new RegleMetierException("Taille attendue entre 1 et 247 caractères.");
        }
        if (s.pad() == null || s.type() == null) {
            throw new RegleMetierException("Indiquez le type et le complément du champ.");
        }
        if (s.decimales() < 0 || s.decimales() > 6 || (s.decimales() > 0 && s.type() != TypeDonnee.NUMERIQUE)) {
            throw new RegleMetierException("Décimales : de 0 à 6, et seulement pour un champ numérique.");
        }
        if (s.confidentiel() && !s.specifique()) {
            throw new RegleMetierException("Un champ confidentiel est forcément stocké (masqué).");
        }
    }

    private static String structure(List<LigneChamp> champs, int positionDonnees) {
        StringBuilder sb = new StringBuilder();
        int p = positionDonnees;
        for (LigneChamp c : champs) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append(p).append('-').append(p + c.taille() - 1).append(' ').append(c.code());
            p += c.taille();
        }
        return sb.toString();
    }

    private void historiser(String format, String objet, String action, String motif, String avant, String apres,
                            String utilisateur) {
        jdbc.sql("""
                        insert into fmt_modification (format_code, objet, action, motif, avant, apres, modifie_par)
                        values (?, ?, ?, ?, ?, ?, ?)""")
                .params(format, objet, action, motif, avant, apres, utilisateur).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_FORMAT, format + " – " + objet, motif, true);
    }

    private Fichier fichier(String format) {
        return jdbc.sql("select id, famille, longueur_enregistrement, position_donnees from fmt_fichier where code = ?")
                .param(format).query(Fichier.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Format inconnu : " + format));
    }

    private LigneChamp ligneChamp(String code) {
        return jdbc.sql("select * from fmt_attribut where code = ?").param(code).query(LigneChamp.class).optional()
                .orElseThrow(() -> new RegleMetierException("Champ inconnu : " + code));
    }

    private static Champ versChamp(LigneChamp c, List<String> utilisations) {
        return new Champ(c.code(), c.libelle(), c.description(), c.taille(), Pad.valueOf(c.pad()), c.decimales(),
                TypeDonnee.valueOf(c.typeDonnee()), c.specifique(), c.confidentiel(), c.protege(), utilisations);
    }

    private static String exigerMotif(String motif) {
        String texte = motif == null ? "" : motif.strip();
        if (texte.length() < 5) {
            throw new RegleMetierException("Indiquez le motif de la modification (ex. bulletin GIM, version de la spécification).");
        }
        if (texte.length() > 500) {
            throw new RegleMetierException("Le motif est limité à 500 caractères.");
        }
        return texte;
    }

    private static void exigerLibelle(String libelle) {
        if (libelle == null || libelle.isBlank() || libelle.strip().length() > 200) {
            throw new RegleMetierException("Le libellé est obligatoire (200 caractères au plus).");
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
