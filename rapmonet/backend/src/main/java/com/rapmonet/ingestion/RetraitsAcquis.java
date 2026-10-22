package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Retraits faits sur les GAB de la banque avec des cartes d'autres banques, lus dans les avis d'acquisition LIS
 * (TC 49, code commerçant 6011). Une banque en processing les reçoit dans le fichier PR ; une banque en switching
 * avec le GIM (cas de la BHCI) les reçoit dans le fichier SW : les deux sont lus.
 */
public interface RetraitsAcquis {

    /**
     * @param cle   identifiant stable de l'avis : nom du fichier LIS et numéro d'enregistrement
     * @param heure heure de la transaction (nulle si absente)
     */
    record Avis(long id, String cle, LocalDate journee, String fichier, String reseau, String carte, String autorisation,
                String rrn, String bordereau, LocalDate dateTransaction, LocalTime heure, String terminal, long montant) {
    }

    /** Avis dont la date de transaction est dans la période, éventuellement pour un seul terminal. */
    List<Avis> avis(LocalDate du, LocalDate au, String terminal);

    /** Dernière journée des fichiers LIS intégrés (SW ou PR). */
    Optional<LocalDate> derniereJournee();
}
