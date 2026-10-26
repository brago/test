package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Activité quotidienne des GAB, calculée sur les journaux NCR intégrés. */
public interface ActiviteJournaux {

    /**
     * Activité d'un GAB pour une journée couverte par un journal.
     *
     * @param sessions              passages de carte
     * @param retraitsDistribues    retraits dont les billets ont été distribués
     * @param retraitsRefuses       retraits refusés (code réponse autre que 00)
     * @param refusParCode          retraits refusés par code réponse
     * @param billetsNonPris        retraits approuvés dont les billets n'ont pas été pris (retirés par le GAB)
     * @param cartesCapturees       sessions avec carte capturée
     * @param annulations           sessions annulées par le client ou après délai dépassé
     * @param incidentsEmv          sessions avec refus de la puce ou sans cryptogramme de l'émetteur
     * @param retraitsParHeure      24 valeurs : retraits distribués par heure
     * @param plusLongueInactivite  plus long intervalle sans passage de carte dans la plage horaire, en minutes
     * @param debutInactivite       début de cet intervalle
     * @param arretes               passages en mode superviseur avec remise à zéro des compteurs
     * @param ajoutsEspeces         passages en mode superviseur avec ajout d'espèces
     */
    record JourGab(String terminal, LocalDate date, int sessions, int retraitsDistribues, long montantDistribue,
                   int retraitsRefuses, Map<String, Integer> refusParCode, int soldes, int billetsNonPris,
                   int cartesCapturees, int annulations, int incidentsEmv, List<Integer> retraitsParHeure,
                   LocalTime premierPassage, LocalTime dernierPassage, int plusLongueInactivite,
                   LocalTime debutInactivite, int arretes, int ajoutsEspeces) {
    }

    /** Jours couverts par un journal, par GAB puis par date ; terminal vide pour tous les GAB. */
    List<JourGab> jours(LocalDate du, LocalDate au, String terminal, LocalTime debutPlage, LocalTime finPlage);

    /** Libellé des codes réponse, par code. */
    Map<String, String> libellesCodesReponse();

    /** Dernière date couverte par un journal, par GAB. */
    Map<String, LocalDate> derniersJournaux();
}
