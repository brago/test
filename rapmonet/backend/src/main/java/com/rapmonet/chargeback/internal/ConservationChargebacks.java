package com.rapmonet.chargeback.internal;

import com.rapmonet.commun.CategorieConservation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Dossiers de chargeback archivés (clos) : seuls ceux-ci sont purgés. */
@Component
class ConservationChargebacks implements CategorieConservation {

    private final JdbcClient jdbc;

    ConservationChargebacks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String code() { return "CHARGEBACKS"; }

    @Override public String libelle() { return "Dossiers de chargeback archivés"; }

    @Override public String description() {
        return "Journée du chargeback ; seuls les dossiers archivés sont supprimés, jamais un dossier en cours.";
    }

    @Override public int dureeMinimaleMois() { return 120; }

    @Override public int dureeParDefautMois() { return 120; }

    @Override
    public long total() {
        return jdbc.sql("select count(*) from chargeback_dossier").query(Long.class).single();
    }

    @Override
    public LocalDate plusAncienne() {
        return jdbc.sql("select min(journee_chargeback) from chargeback_dossier").query(LocalDate.class).optional().orElse(null);
    }

    @Override
    public long aPurger(LocalDate limite) {
        return jdbc.sql("select count(*) from chargeback_dossier where archive and journee_chargeback < ?").param(limite)
                .query(Long.class).single();
    }

    @Override
    public long purger(LocalDate limite) {
        return jdbc.sql("delete from chargeback_dossier where archive and journee_chargeback < ?").param(limite).update();
    }
}
