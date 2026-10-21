package com.rapmonet.compense.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Frais d'un achat, comme la macro Excel : montant × taux = commission TTC ; commission HT = TTC / (1 + taux de TAF)
 * arrondie ; TAF = TTC − commission, arrondie. Arrondi à l'unité, demi vers le haut (fonction ARRONDI d'Excel).
 *
 * <p>Le franc CFA n'a pas de décimales : la CCI est arrondie à l'unité et vaut les frais prélevés au commerçant
 * (commission + TAF). Commission et TAF restent calculées sur la valeur non arrondie, comme la macro.
 */
public record CalculFrais(BigDecimal cci, long frais, long commission, long taf) {

    public static CalculFrais calculer(long montant, BigDecimal taux, BigDecimal tauxTaf) {
        BigDecimal cci = BigDecimal.valueOf(montant).multiply(taux);
        long commission = arrondi(cci.divide(BigDecimal.ONE.add(tauxTaf), 10, RoundingMode.HALF_UP));
        long taf = arrondi(cci.subtract(BigDecimal.valueOf(commission)));
        long frais = arrondi(cci);
        return new CalculFrais(BigDecimal.valueOf(frais), frais, commission, taf);
    }

    private static long arrondi(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
