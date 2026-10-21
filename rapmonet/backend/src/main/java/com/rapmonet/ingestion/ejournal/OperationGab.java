package com.rapmonet.ingestion.ejournal;

import java.time.LocalDateTime;

/**
 * Opération lue sur un ticket du journal : retrait ou demande de solde.
 *
 * @param reference numéro imprimé après la date et l'heure du ticket (référence de la transaction)
 * @param montant   montant demandé (retrait), en unités de la devise
 */
public record OperationGab(TypeOperationGab type, String carte, String terminal, LocalDateTime dateHeure,
                           String reference, Long montant, String devise, String codeReponse, String compte,
                           int numeroLigne) {
}
