package com.rapmonet.ingestion;

import com.rapmonet.commun.PageResultat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Consultation des transactions LIS intégrées. */
public interface ConsultationLis {

    record Critere(LocalDate du, LocalDate au, TypeLis typeLis, String tc) {
    }

    record TransactionLis(long id, LocalDate journee, TypeLis typeLis, String nomFichier, String sousFichier,
                          String tc, String libelleTc, int sequence, int nbTcr, Map<String, Object> attributs) {
    }

    record ValeurAttribut(String code, String libelle, int position, int taille, String brut,
                          Object valeur, String erreur) {
    }

    record LigneDetail(int sequence, String tcr, String contenu, List<ValeurAttribut> attributs) {
    }

    record DetailTransaction(TransactionLis transaction, List<LigneDetail> lignes) {
    }

    PageResultat<TransactionLis> rechercher(Critere critere, int page, int taille);

    Optional<DetailTransaction> detail(long transactionId);
}
