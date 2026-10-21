package com.rapmonet.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Achats faits sur les TPE de la banque, lus dans les avis d'acquisition LIS (TC 49 hors retraits). */
public interface AchatsAcquis {

    /**
     * @param reseau      GIM, VISA ou MASTERCARD (indicateur de produit de paiement du TC 49)
     * @param interchange frais d'interchange du LIS ; nul pour une carte de la banque (on-us)
     */
    record Achat(long id, LocalDate journee, String fichier, String reseau, String carte, String autorisation,
                 String arn, String rrn, LocalDate dateTransaction, String heureTransaction, String terminal,
                 String marchand, String ville, long montant, BigDecimal interchange) {
    }

    /** Achats des fichiers LIS de la journée, dans l'ordre des fichiers. */
    List<Achat> achats(LocalDate journee);

    /** Journées LIS intégrées contenant des achats, de la plus récente à la plus ancienne. */
    List<LocalDate> journees();
}
