package com.rapmonet.securite.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le compte « admin » (profil ADMIN) au premier démarrage, quand aucun utilisateur n'existe.
 * Son mot de passe initial devra être changé à la première connexion.
 */
@Component
class InitialisationAdministrateur implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InitialisationAdministrateur.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder encodeur;
    private final String motDePasseInitial;

    InitialisationAdministrateur(JdbcClient jdbc, PasswordEncoder encodeur,
                                 @Value("${rapmonet.securite.admin.mot-de-passe-initial}") String motDePasseInitial) {
        this.jdbc = jdbc;
        this.encodeur = encodeur;
        this.motDePasseInitial = motDePasseInitial;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.sql("select count(*) from securite_utilisateur").query(Integer.class).single() > 0) {
            return;
        }
        jdbc.sql("""
                        insert into securite_utilisateur (login, nom, profil_code, mot_de_passe, doit_changer_mdp)
                        values ('admin', 'Administrateur', 'ADMIN', ?, true)""")
                .param(encodeur.encode(motDePasseInitial)).update();
        LOG.warn("Compte « admin » créé : changez son mot de passe à la première connexion.");
    }
}
