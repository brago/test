package com.rapmonet.ingestion.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.Alerte.Niveau;
import com.rapmonet.alertes.SourceAlertes;
import com.rapmonet.ingestion.BatchJournaux;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Dernière exécution du batch des journaux en erreur ou avec rejets ; fichiers LIS en retard. */
@Component
class AlertesIngestion implements SourceAlertes {

    /** Au-delà de 3 jours sans nouvelle journée LIS, une alerte est levée. */
    static final int JOURS_SANS_LIS = 3;

    private static final DateTimeFormatter JJ = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BatchJournaux batch;
    private final JdbcClient jdbc;

    AlertesIngestion(BatchJournaux batch, JdbcClient jdbc) {
        this.batch = batch;
        this.jdbc = jdbc;
    }

    @Override
    public List<Alerte> alertes(LocalDate aujourdhui) {
        List<Alerte> l = new ArrayList<>();
        batch.etat().dernieresExecutions().stream().findFirst().ifPresent(e -> {
            if ("ERREUR".equals(e.statut())) {
                l.add(new Alerte("BATCH_JOURNAUX_ERREUR", Niveau.CRITIQUE, "Batch des journaux GAB en erreur",
                        "Dernière exécution le " + JJ.format(e.debut()) + (e.message() == null ? "" : " : " + e.message()),
                        1, null, "CHARGEMENT_EJOURNAL", "/ingestion/ejournal"));
            } else if (e.nbRejetes() > 0) {
                l.add(new Alerte("BATCH_JOURNAUX_REJETS", Niveau.ATTENTION, "Journaux GAB rejetés par le batch",
                        "Exécution du " + JJ.format(e.debut()) + " : voir la raison de chaque rejet dans l'historique.",
                        e.nbRejetes(), null, "CHARGEMENT_EJOURNAL", "/ingestion/ejournal"));
            }
        });
        LocalDate derniere = jdbc.sql("select max(journee) from lis_fichier").query(LocalDate.class).optional().orElse(null);
        if (derniere == null) {
            l.add(new Alerte("LIS_AUCUN", Niveau.INFO, "Aucun fichier LIS chargé",
                    "Charger le fichier SW du jour.", 1, null, "CHARGEMENT_LIS", "/ingestion"));
        } else if (derniere.isBefore(aujourdhui.minusDays(JOURS_SANS_LIS))) {
            l.add(new Alerte("LIS_EN_RETARD", Niveau.ATTENTION, "Fichiers LIS en retard",
                    "Dernière journée chargée : " + JJ.format(derniere) + ". Charger les fichiers SW manquants.",
                    1, null, "CHARGEMENT_LIS", "/ingestion"));
        }
        return l;
    }
}
