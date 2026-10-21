package com.rapmonet.compense.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Valeurs reprises d'une pièce comptable produite par la macro Excel (montant, taux → CCI, COMM ACQUI, TAF). */
class CalculFraisTest {

    private static final BigDecimal TAF = new BigDecimal("0.17");

    @Test
    void commissionEtTafCommeLaMacro() {
        var a = CalculFrais.calculer(21750, new BigDecimal("0.01"), TAF);
        assertThat(a.cci()).isEqualByComparingTo("217.5");
        assertThat(a.frais()).isEqualTo(218);
        assertThat(a.commission()).isEqualTo(186);
        assertThat(a.taf()).isEqualTo(32);

        var b = CalculFrais.calculer(13950, new BigDecimal("0.0125"), TAF);
        assertThat(b.cci()).isEqualByComparingTo("174.375");
        assertThat(b.commission()).isEqualTo(149);
        assertThat(b.taf()).isEqualTo(25);

        var c = CalculFrais.calculer(20301, new BigDecimal("0.02"), TAF);
        assertThat(c.cci()).isEqualByComparingTo("406.02");
        assertThat(c.commission()).isEqualTo(347);
        assertThat(c.taf()).isEqualTo(59);
    }

    @Test
    void fraisPrelevesEgalCommissionPlusTaf() {
        for (long montant = 1000; montant < 200_000; montant += 777) {
            for (String taux : new String[]{"0.01", "0.0125", "0.015", "0.02", "0.025"}) {
                var f = CalculFrais.calculer(montant, new BigDecimal(taux), TAF);
                assertThat(f.commission() + f.taf()).as("%d à %s", montant, taux).isEqualTo(f.frais());
            }
        }
    }

    @Test
    void tauxNulAucunFrais() {
        var f = CalculFrais.calculer(50000, BigDecimal.ZERO, TAF);
        assertThat(f.frais()).isZero();
        assertThat(f.commission()).isZero();
        assertThat(f.taf()).isZero();
    }
}
