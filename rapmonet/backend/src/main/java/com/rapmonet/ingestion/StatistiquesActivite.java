package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Volumes d'activité agrégés par entité (GAB, TPE ou BIN) et par mois, pour les indicateurs et les états. */
public interface StatistiquesActivite {

    enum Source {
        /** Retraits de cartes d'autres banques sur les GAB de la banque : LIS, TC 49, code commerçant 6011. */
        LIS_ACQUISITION_RETRAITS,
        /** Achats de cartes d'autres banques sur les TPE de la banque : LIS, TC 49, autres codes commerçant. */
        LIS_ACQUISITION_ACHATS,
        /** Retraits des cartes de la banque chez d'autres banques : LIS du switch, TC 07, première présentation. */
        LIS_EMISSION_RETRAITS,
        /** Achats des cartes de la banque chez des commerçants d'autres banques : LIS du switch, TC 05. */
        LIS_EMISSION_ACHATS,
        /** Retraits distribués par les GAB de la banque, toutes cartes : journaux électroniques, par GAB. */
        JOURNAUX_RETRAITS,
        /** Retraits distribués par les GAB de la banque, regroupés par BIN de la carte : journaux électroniques. */
        JOURNAUX_RETRAITS_PAR_BIN
    }

    /**
     * @param entite  terminal ou BIN (6 premiers chiffres de la carte)
     * @param libelle nom du commerçant ou du GAB tel qu'il figure dans le fichier, s'il existe
     * @param reseau  GIM, VISA ou MASTERCARD pour les LIS ; vide pour les journaux
     */
    record Agregat(String entite, String libelle, String reseau, YearMonth mois, long nombre, long montant) {
    }

    /** Agrégats par entité, réseau et mois, pour les transactions datées du premier au dernier jour inclus. */
    List<Agregat> agreger(Source source, LocalDate du, LocalDate au);
}
