package com.rapmonet.commun;

import java.time.LocalDate;

/**
 * Catégorie de données soumise à une durée de conservation. Chaque module déclare les siennes ; la purge supprime les
 * données dont la date de référence est antérieure à la date limite (aujourd'hui moins la durée de conservation).
 */
public interface CategorieConservation {

    /** Code stable, utilisé dans les paramètres conservation.CODE.mois. */
    String code();

    String libelle();

    /** Date de référence et éléments supprimés (ex. fichiers avec leurs transactions). */
    String description();

    /** Durée minimale autorisée, en mois (obligations légales ou besoins de contrôle). */
    int dureeMinimaleMois();

    /** Durée appliquée tant qu'aucune autre n'est paramétrée, en mois. */
    int dureeParDefautMois();

    /** Nombre total d'éléments conservés. */
    long total();

    /** Date de référence la plus ancienne, nulle si aucune donnée. */
    LocalDate plusAncienne();

    /** Nombre d'éléments dont la date de référence est antérieure à la limite. */
    long aPurger(LocalDate limite);

    /** Supprime ces éléments et renvoie leur nombre. */
    long purger(LocalDate limite);
}
