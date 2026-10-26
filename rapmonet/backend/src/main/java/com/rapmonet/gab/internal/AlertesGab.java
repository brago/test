package com.rapmonet.gab.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.Alerte.Niveau;
import com.rapmonet.alertes.SourceAlertes;
import com.rapmonet.ingestion.ConsultationJournaux;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Appros et arrêtés à valider ; GAB actifs sans journal la veille. */
@Component
class AlertesGab implements SourceAlertes {

    private static final DateTimeFormatter JJ = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcClient jdbc;
    private final ConsultationJournaux journaux;

    AlertesGab(JdbcClient jdbc, ConsultationJournaux journaux) {
        this.jdbc = jdbc;
        this.journaux = journaux;
    }

    @Override
    public List<Alerte> alertes(LocalDate aujourdhui) {
        List<Alerte> l = new ArrayList<>();
        l.add(aValider("gab_appro", "GAB_APPRO_A_VALIDER", "Approvisionnements à valider", aujourdhui));
        l.add(aValider("gab_arrete", "GAB_ARRETE_A_VALIDER", "Arrêtés à valider", aujourdhui));
        LocalDate hier = aujourdhui.minusDays(1);
        List<String> sansJournal = jdbc.sql("select terminal_id from gab where actif order by terminal_id")
                .query(String.class).list().stream()
                .filter(t -> journaux.joursCouverts(t, hier, hier) == 0).toList();
        String liste = String.join(", ", sansJournal.stream().limit(5).toList()) + (sansJournal.size() > 5 ? "…" : "");
        l.add(new Alerte("GAB_SANS_JOURNAL", Niveau.ATTENTION, "GAB sans journal pour le " + JJ.format(hier),
                "Journal non intégré : " + liste + ". Vérifier le dépôt et le dossier de rejet du batch.",
                sansJournal.size(), null, "CHARGEMENT_EJOURNAL", "/ingestion/ejournal"));
        return l;
    }

    /** Saisies au statut SAISI ; attention dès qu'une attend depuis plus d'un jour. */
    private Alerte aValider(String table, String code, String titre, LocalDate aujourdhui) {
        return jdbc.sql("select count(*), min(saisi_le) from " + table + " where statut = 'SAISI'")
                .query((rs, n) -> {
                    long nb = rs.getLong(1);
                    OffsetDateTime plusAncienne = rs.getObject(2, OffsetDateTime.class);
                    boolean ancienne = plusAncienne != null && plusAncienne.toLocalDate().isBefore(aujourdhui.minusDays(1));
                    return new Alerte(code, ancienne ? Niveau.ATTENTION : Niveau.INFO, titre,
                            plusAncienne == null ? "" : "La plus ancienne date du " + JJ.format(plusAncienne)
                                    + " : validation par un autre agent que l'auteur.",
                            nb, null, "GAB_VALIDATION", "/gab/validation");
                })
                .single();
    }
}
