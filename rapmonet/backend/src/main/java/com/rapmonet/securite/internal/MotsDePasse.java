package com.rapmonet.securite.internal;

import com.rapmonet.commun.web.RegleMetierException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Enregistrement des mots de passe : composition, historique des anciens mots de passe, date de modification. */
@Component
class MotsDePasse {

    private final JdbcClient jdbc;
    private final PasswordEncoder encodeur;
    private final PolitiqueSecurite politique;

    MotsDePasse(JdbcClient jdbc, PasswordEncoder encodeur, PolitiqueSecurite politique) {
        this.jdbc = jdbc;
        this.encodeur = encodeur;
        this.politique = politique;
    }

    /**
     * Contrôle puis enregistre un nouveau mot de passe. L'ancien rejoint l'historique.
     *
     * @param controlerHistorique false pour un mot de passe temporaire défini par l'administrateur
     */
    void changer(String login, String nouveau, boolean controlerHistorique, boolean doitChanger) {
        politique.verifierComposition(login, nouveau);
        String actuelle = jdbc.sql("select mot_de_passe from securite_utilisateur where login = ?").param(login)
                .query(String.class).single();
        if (controlerHistorique) {
            if (encodeur.matches(nouveau, actuelle)) {
                throw new RegleMetierException("Le nouveau mot de passe doit être différent de l'actuel.");
            }
            int n = politique.lire().historique();
            if (n > 1 && anciennes(login, n - 1).stream().anyMatch(e -> encodeur.matches(nouveau, e))) {
                throw new RegleMetierException("Ce mot de passe a déjà été utilisé : les " + n
                        + " derniers mots de passe ne peuvent pas être réutilisés.");
            }
        }
        jdbc.sql("insert into securite_mdp_historique (login, empreinte) values (?, ?)").params(login, actuelle).update();
        jdbc.sql("""
                        delete from securite_mdp_historique where login = ? and id not in (
                            select id from securite_mdp_historique where login = ? order by id desc limit 24)""")
                .params(login, login).update();
        jdbc.sql("""
                        update securite_utilisateur set mot_de_passe = ?, doit_changer_mdp = ?, mdp_modifie_le = now(),
                            modifie_le = now() where login = ?""")
                .params(encodeur.encode(nouveau), doitChanger, login).update();
    }

    private List<String> anciennes(String login, int nombre) {
        return jdbc.sql("select empreinte from securite_mdp_historique where login = ? order by id desc limit ?")
                .params(login, nombre).query(String.class).list();
    }
}
