package com.rapmonet.compense.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.Alerte.Niveau;
import com.rapmonet.alertes.SourceAlertes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Traitements de compense en attente de validation. */
@Component
class AlertesCompense implements SourceAlertes {

    private final JdbcClient jdbc;

    AlertesCompense(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Alerte> alertes(LocalDate aujourdhui) {
        return jdbc.sql("select count(*), coalesce(sum(montant), 0) from compense_traitement where statut = 'SAISI'")
                .query((rs, n) -> new Alerte("COMPENSE_A_VALIDER", Niveau.ATTENTION, "Compenses TPE à valider",
                        "Traitements créés, en attente de validation par un autre agent.", rs.getLong(1), rs.getLong(2),
                        "COMPENSE_VALIDATION", "/compense/validation"))
                .list();
    }
}
