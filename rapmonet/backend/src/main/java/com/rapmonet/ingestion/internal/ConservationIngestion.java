package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.CategorieConservation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

/** Données chargées : fichiers LIS, journaux GAB, rapports VSS, historique des traitements automatiques. */
@Configuration
class ConservationIngestion {

    /** Catégorie définie par une table et sa colonne de date (les lignes filles sont supprimées en cascade). */
    record CategorieTable(JdbcClient jdbc, String code, String libelle, String description, int dureeMinimaleMois,
                          int dureeParDefautMois, String table, String colonneDate) implements CategorieConservation {

        @Override
        public long total() {
            return jdbc.sql("select count(*) from " + table).query(Long.class).single();
        }

        @Override
        public LocalDate plusAncienne() {
            return jdbc.sql("select cast(min(" + colonneDate + ") as date) from " + table).query(LocalDate.class).optional().orElse(null);
        }

        @Override
        public long aPurger(LocalDate limite) {
            return jdbc.sql("select count(*) from " + table + " where " + colonneDate + " < ?").param(limite)
                    .query(Long.class).single();
        }

        @Override
        public long purger(LocalDate limite) {
            return jdbc.sql("delete from " + table + " where " + colonneDate + " < ?").param(limite).update();
        }
    }

    @Bean
    CategorieConservation conservationLis(JdbcClient jdbc) {
        return new CategorieTable(jdbc, "LIS", "Fichiers LIS",
                "Journée du fichier ; le fichier, ses enregistrements et ses transactions sont supprimés.",
                120, 120, "lis_fichier", "journee");
    }

    @Bean
    CategorieConservation conservationJournaux(JdbcClient jdbc) {
        return new CategorieTable(jdbc, "JOURNAUX_GAB", "Journaux GAB",
                "Dernière date du journal ; le journal, ses sessions, opérations et passages du gabiste sont supprimés.",
                120, 120, "ej_fichier", "coalesce(date_fin, cast(integre_le as date))");
    }

    @Bean
    CategorieConservation conservationVss(JdbcClient jdbc) {
        return new CategorieTable(jdbc, "VSS", "Rapports VSS (VISA)",
                "Date de traitement du fichier ; le fichier et ses rapports sont supprimés.",
                120, 120, "vss_fichier", "coalesce(date_traitement, cast(integre_le as date))");
    }

    @Bean
    CategorieConservation conservationTraitements(JdbcClient jdbc) {
        return new CategorieTable(jdbc, "TRAITEMENTS_AUTOMATIQUES", "Historique des traitements automatiques",
                "Date d'exécution du batch (journaux GAB, LIS) ; seul l'historique est supprimé, pas les données intégrées.",
                3, 24, "batch_execution", "debut");
    }
}
