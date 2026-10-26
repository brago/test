package com.rapmonet.chargeback;

import java.time.LocalDate;
import java.util.List;

/** Dossiers de chargeback d'un porteur, pour le traitement d'une réclamation. */
public interface DossiersPorteur {

    /**
     * Dossier de chargeback résumé.
     *
     * @param sens     EMISSION (carte de la banque) ou ACQUISITION (GAB ou TPE de la banque)
     * @param etat     EN_ATTENTE, REPRESENTE, SECOND_CHARGEBACK, ANNULE, DELAI_ECOULE
     */
    record DossierPorteur(long id, String sens, String arn, String libelleTc, String rrn, String carte, String terminal,
                          String marchand, LocalDate dateTransaction, Long montant, String motif,
                          LocalDate journeeChargeback, String etat, String situation, String regularisation,
                          boolean archive) {
    }

    /** Par carte (6 premiers et 4 derniers chiffres, ou empreinte du numéro complet) ou par ARN, RRN ou autorisation. */
    List<DossierPorteur> dossiers(String debutCarte, String finCarte, String empreinte, String reference,
                                  LocalDate du, LocalDate au);
}
