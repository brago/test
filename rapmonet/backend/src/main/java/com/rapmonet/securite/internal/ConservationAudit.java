package com.rapmonet.securite.internal;

import com.rapmonet.commun.CategorieConservation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Piste d'audit. */
@Component
class ConservationAudit implements CategorieConservation {

    private final JdbcClient jdbc;

    ConservationAudit(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String code() { return "AUDIT"; }

    @Override public String libelle() { return "Piste d'audit"; }

    @Override public String description() {
        return "Date de l'événement (connexions, modifications, exports, purges…).";
    }

    @Override public int dureeMinimaleMois() { return 60; }

    @Override public int dureeParDefautMois() { return 120; }

    @Override
    public long total() {
        return jdbc.sql("select count(*) from audit_evenement").query(Long.class).single();
    }

    @Override
    public LocalDate plusAncienne() {
        return jdbc.sql("select cast(min(horodatage) as date) from audit_evenement").query(LocalDate.class).optional().orElse(null);
    }

    @Override
    public long aPurger(LocalDate limite) {
        return jdbc.sql("select count(*) from audit_evenement where horodatage < ?").param(limite.atStartOfDay())
                .query(Long.class).single();
    }

    @Override
    public long purger(LocalDate limite) {
        return jdbc.sql("delete from audit_evenement where horodatage < ?").param(limite.atStartOfDay()).update();
    }
}
