package com.rapmonet.securite.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.securite.ActionsAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Profils : un code, un libellé et un ensemble de menus autorisés. */
@Service
public class ProfilsService {

    public record Menu(String code, String libelle, String parentCode, int rang) {
    }

    public record Profil(String code, String libelle, String description, boolean systeme,
                         List<String> menus, int nbUtilisateurs) {
    }

    public record Saisie(String code, String libelle, String description, List<String> menus) {
    }

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{2,30}");

    private final JdbcClient jdbc;
    private final AuditService audit;

    ProfilsService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Menu> menus() {
        return jdbc.sql("""
                        with recursive arbre as (
                            select code, libelle, parent_code, rang, lpad(rang::text, 3, '0') as chemin
                            from securite_menu where parent_code is null
                            union all
                            select m.code, m.libelle, m.parent_code, m.rang, a.chemin || '.' || lpad(m.rang::text, 3, '0')
                            from securite_menu m join arbre a on m.parent_code = a.code)
                        select code, libelle, parent_code, rang from arbre order by chemin""")
                .query(Menu.class).list();
    }

    @Transactional(readOnly = true)
    public List<Profil> lister() {
        return jdbc.sql("select code from securite_profil order by systeme desc, code").query(String.class).list()
                .stream().map(this::trouver).toList();
    }

    @Transactional(readOnly = true)
    public Profil trouver(String code) {
        record Ligne(String code, String libelle, String description, boolean systeme) {
        }
        Ligne l = jdbc.sql("select code, libelle, description, systeme from securite_profil where code = ?")
                .param(code).query(Ligne.class).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Profil inconnu : " + code));
        List<String> menus = jdbc.sql("""
                        select pm.menu_code from securite_profil_menu pm join securite_menu m on m.code = pm.menu_code
                        where pm.profil_code = ? order by m.code""").param(code).query(String.class).list();
        int nb = jdbc.sql("select count(*) from securite_utilisateur where profil_code = ?").param(code)
                .query(Integer.class).single();
        return new Profil(l.code(), l.libelle(), l.description(), l.systeme(), menus, nb);
    }

    @Transactional
    public Profil creer(Saisie s) {
        if (s.code() == null || !CODE.matcher(s.code()).matches()) {
            throw new RegleMetierException("Code invalide : 2 à 30 caractères, majuscules, chiffres ou _.");
        }
        if (jdbc.sql("select count(*) from securite_profil where code = ?").param(s.code()).query(Integer.class).single() > 0) {
            throw new RegleMetierException("Le profil « " + s.code() + " » existe déjà.");
        }
        exigerLibelle(s.libelle());
        Set<String> menus = verifierMenus(s.menus());
        jdbc.sql("insert into securite_profil (code, libelle, description) values (?, ?, ?)")
                .params(s.code(), s.libelle().strip(), s.description()).update();
        enregistrerMenus(s.code(), menus);
        audit.enregistrer(ActionsAudit.PROFIL_CREATION, s.code(), "Menus : " + String.join(", ", menus));
        return trouver(s.code());
    }

    @Transactional
    public Profil modifier(String code, Saisie s) {
        Profil avant = trouver(code);
        if (avant.systeme()) {
            throw new RegleMetierException("Le profil « " + code + " » est protégé et ne peut pas être modifié.");
        }
        exigerLibelle(s.libelle());
        Set<String> menus = verifierMenus(s.menus());
        jdbc.sql("update securite_profil set libelle = ?, description = ?, modifie_le = now() where code = ?")
                .params(s.libelle().strip(), s.description(), code).update();
        jdbc.sql("delete from securite_profil_menu where profil_code = ?").param(code).update();
        enregistrerMenus(code, menus);
        Set<String> ajoutes = new TreeSet<>(menus);
        avant.menus().forEach(ajoutes::remove);
        Set<String> retires = new TreeSet<>(avant.menus());
        retires.removeAll(menus);
        audit.enregistrer(ActionsAudit.PROFIL_MODIFICATION, code,
                "Ajoutés : " + (ajoutes.isEmpty() ? "aucun" : String.join(", ", ajoutes))
                        + " ; retirés : " + (retires.isEmpty() ? "aucun" : String.join(", ", retires)));
        return trouver(code);
    }

    @Transactional
    public void supprimer(String code) {
        Profil p = trouver(code);
        if (p.systeme()) {
            throw new RegleMetierException("Le profil « " + code + " » est protégé et ne peut pas être supprimé.");
        }
        if (p.nbUtilisateurs() > 0) {
            throw new RegleMetierException("Le profil est attribué à " + p.nbUtilisateurs() + " utilisateur(s).");
        }
        jdbc.sql("delete from securite_profil where code = ?").param(code).update();
        audit.enregistrer(ActionsAudit.PROFIL_SUPPRESSION, code, null);
    }

    /** Vérifie que chaque menu existe et que son parent est aussi autorisé. */
    private Set<String> verifierMenus(List<String> demandes) {
        Map<String, Menu> parCode = menus().stream().collect(Collectors.toMap(Menu::code, m -> m));
        Set<String> menus = new HashSet<>(demandes == null ? List.of() : demandes);
        for (String m : menus) {
            Menu menu = parCode.get(m);
            if (menu == null) {
                throw new RegleMetierException("Menu inconnu : " + m);
            }
            if (menu.parentCode() != null && !menus.contains(menu.parentCode())) {
                throw new RegleMetierException("Le menu « " + menu.libelle() + " » exige d'autoriser d'abord « "
                        + parCode.get(menu.parentCode()).libelle() + " ».");
            }
        }
        return new TreeSet<>(menus);
    }

    private void enregistrerMenus(String profil, Set<String> menus) {
        menus.forEach(m -> jdbc.sql("insert into securite_profil_menu (profil_code, menu_code) values (?, ?)")
                .params(profil, m).update());
    }

    private static void exigerLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new RegleMetierException("Le libellé est obligatoire.");
        }
    }
}
