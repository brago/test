package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.util.List;

/**
 * Opérations LIS de la famille présentation / chargeback / redressement (TC 05 à 37), pour le suivi
 * des chargebacks. Le numéro de carte est masqué ; son empreinte permet les rapprochements.
 */
public interface LitigesLis {

    /**
     * @param journee      journée du fichier LIS qui contient l'opération
     * @param usage        code usage : 1 = première présentation ou premier chargeback, 2 = représentation ou second chargeback
     * @param fid          institution émettrice du message (forwarding institution)
     * @param rid          institution destinataire du message (receiving institution)
     */
    record OperationLitige(long id, LocalDate journee, String nomFichier, String tc, String usage, String arn, String rrn,
                           String carte, String carteEmpreinte, String terminal, String marchand,
                           LocalDate dateTransaction, String heureTransaction, String autorisation, Long montant,
                           String devise, String motif, String referenceChargeback, String message, String fid,
                           String rid) {
    }

    /** Chargebacks (TC 15, 16, 17) intégrés, toutes journées confondues. */
    List<OperationLitige> chargebacks();

    /** Toutes les opérations (TC 05 à 37) portant cet ARN, de la plus ancienne à la plus récente. */
    List<OperationLitige> cycle(String arn);
}
