package com.rapmonet.rapprochement.internal;

import com.rapmonet.commun.CategorieConservation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Traitements des écarts de rapprochement (justifiés ou régularisés). */
@Component
class ConservationEcarts implements CategorieConservation {

    private final JdbcClient jdbc;

    ConservationEcarts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override public String code() { return "ECARTS_RAPPROCHEMENT"; }

    @Override public String libelle() { return "Traitements des écarts de rapprochement"; }

    @Override public String description() {
        return "Date du traitement (justification ou régularisation) ; à aligner sur la conservation des journaux et des LIS.";
    }

    @Override public int dureeMinimaleMois() { return 120; }

    @Override public int dureeParDefautMois() { return 120; }

    @Override
    public long total() {
        return jdbc.sql("select count(*) from rapprochement_ecart").query(Long.class).single();
    }

    @Override
    public LocalDate plusAncienne() {
        return jdbc.sql("select cast(min(traite_le) as date) from rapprochement_ecart").query(LocalDate.class).optional().orElse(null);
    }

    @Override
    public long aPurger(LocalDate limite) {
        return jdbc.sql("select count(*) from rapprochement_ecart where traite_le < ?").param(limite.atStartOfDay())
                .query(Long.class).single();
    }

    @Override
    public long purger(LocalDate limite) {
        return jdbc.sql("delete from rapprochement_ecart where traite_le < ?").param(limite.atStartOfDay()).update();
    }
}
