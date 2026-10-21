package com.rapmonet.compense.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Frais d'un achat, comme la macro Excel : CCI = montant × taux (commission TTC) ; frais prélevés au
 * commerçant = CCI arrondie ; commission HT = CCI / (1 + taux de TAF) arrondie ; TAF = CCI − commission, arrondie.
 * Arrondi à l'unité, demi vers le haut (fonction ARRONDI d'Excel).
 */
public record CalculFrais(BigDecimal cci, long frais, long commission, long taf) {

    public static CalculFrais calculer(long montant, BigDecimal taux, BigDecimal tauxTaf) {
        BigDecimal cci = BigDecimal.valueOf(montant).multiply(taux);
        long commission = arrondi(cci.divide(BigDecimal.ONE.add(tauxTaf), 10, RoundingMode.HALF_UP));
        long taf = arrondi(cci.subtract(BigDecimal.valueOf(commission)));
        return new CalculFrais(cci.setScale(4, RoundingMode.HALF_UP), arrondi(cci), commission, taf);
    }

    private static long arrondi(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
