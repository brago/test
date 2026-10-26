package com.rapmonet.reclamation.internal;

import com.rapmonet.commun.CategorieConservation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Réclamations closes ; une réclamation ouverte ou en cours n'est jamais purgée. */
@Component
class ConservationReclamations implements CategorieConservation {

    private final JdbcClient jdbc;

    ConservationReclamations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String code() { return "RECLAMATIONS"; }

    @Override public String libelle() { return "Réclamations porteurs closes"; }

    @Override public String description() {
        return "Date de clôture ; la réclamation et son suivi sont supprimés. Les réclamations non closes sont conservées.";
    }

    @Override public int dureeMinimaleMois() { return 60; }

    @Override public int dureeParDefautMois() { return 120; }

    @Override
    public long total() {
        return jdbc.sql("select count(*) from reclamation").query(Long.class).single();
    }

    @Override
    public LocalDate plusAncienne() {
        return jdbc.sql("select cast(min(cree_le) as date) from reclamation").query(LocalDate.class).optional().orElse(null);
    }

    @Override
    public long aPurger(LocalDate limite) {
        return jdbc.sql("select count(*) from reclamation where statut in ('FONDEE', 'NON_FONDEE') and cloture_le < ?")
                .param(limite.atStartOfDay()).query(Long.class).single();
    }

    @Override
    public long purger(LocalDate limite) {
        return jdbc.sql("delete from reclamation where statut in ('FONDEE', 'NON_FONDEE') and cloture_le < ?")
                .param(limite.atStartOfDay()).update();
    }
}
