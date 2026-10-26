package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.util.List;

/** Recherche des opérations d'un porteur (carte, compte, référence) dans les journaux GAB et les fichiers LIS. */
public interface OperationsPorteur {

    /**
     * Critères ; au moins un parmi carte, compte et référence.
     *
     * @param debutCarte  6 premiers chiffres de la carte
     * @param finCarte    4 derniers chiffres
     * @param empreinte   empreinte du numéro complet quand il est connu (correspondance exacte dans les LIS)
     * @param reference   référence du ticket, code d'autorisation, RRN ou ARN
     */
    record Critere(LocalDate du, LocalDate au, String debutCarte, String finCarte, String empreinte, String compte,
                   String reference) {
    }

    List<ConsultationJournaux.Operation> operationsGab(Critere critere, int maximum);

    List<ConsultationLis.TransactionLis> transactionsLis(Critere critere, int maximum);

    /** Empreinte d'un numéro de carte complet (jamais conservé). */
    String empreinte(String numeroCarte);
}
