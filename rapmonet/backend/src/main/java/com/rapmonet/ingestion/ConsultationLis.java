package com.rapmonet.ingestion;

import com.rapmonet.commun.PageResultat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Consultation des transactions LIS intégrées. */
public interface ConsultationLis {

    record Critere(LocalDate du, LocalDate au, TypeLis typeLis, String tc) {
    }

    /**
     * Valeurs principales d'une transaction ; les codes d'attributs différant selon le TC,
     * chaque valeur est la première trouvée parmi plusieurs attributs.
     */
    record Synthese(LocalDate date, Number montant, String carte, String libelle, String terminal,
                    String autorisation, String reference) {
    }

    record TransactionLis(long id, LocalDate journee, TypeLis typeLis, String nomFichier, String sousFichier,
                          String tc, String libelleTc, int sequence, int nbTcr, Synthese synthese,
                          Map<String, Object> attributs) {
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

    long compter(Critere critere);

    /** Parcourt toutes les transactions correspondant aux critères, dans l'ordre d'affichage. */
    void parcourir(Critere critere, Consumer<TransactionLis> consommateur);
}
