package com.rapmonet.chargeback.internal;

import com.rapmonet.chargeback.internal.ChargebacksService.Sens;
import com.rapmonet.ingestion.LitigesLis.OperationLitige;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cycle de vie d'un chargeback reconstitué à partir des opérations LIS d'un même ARN. */
class CycleChargebackTest {

    private static final LocalDate J = LocalDate.of(2026, 3, 2);

    private static OperationLitige op(String tc, String usage, LocalDate journee) {
        return new OperationLitige(1, journee, "LIS", tc, usage, "ARN", "RRN", "400000******0001", null, "GAB1", "GAB",
                journee, "000000", "123456", 10000L, "952", "0090", "000001", null, null, null);
    }

    @Test
    void chargebackRecuSansPremierePresentationNiRepresentation() {
        var c = ChargebacksService.cycle(Sens.ACQUISITION, J, List.of(op("17", "1", J)), 45, J.plusDays(10));
        assertThat(c.etat()).isEqualTo("EN_ATTENTE");
        assertThat(c.situation()).isEqualTo("À traiter avant le 16/04/2026");
        assertThat(c.lignes().get(0).situation()).isEqualTo("Transaction non compensée dans les fichiers LIS intégrés");
        assertThat(c.lignes().get(0).alerte()).isTrue();
    }

    @Test
    void emissionSansRepresentationDansLeDelaiEstAcquise() {
        var c = ChargebacksService.cycle(Sens.EMISSION, J, List.of(op("07", "1", J.minusDays(20)), op("17", "1", J)), 45,
                J.plusDays(46));
        assertThat(c.etat()).isEqualTo("DELAI_ECOULE");
        assertThat(c.situation()).isEqualTo("Chargeback acquis");
        assertThat(c.lignes().get(2).situation()).contains("chargeback acquis");
    }

    @Test
    void secondChargebackEtAnnulation() {
        var ops = List.of(op("07", "1", J.minusDays(20)), op("17", "1", J), op("07", "2", J.plusDays(5)),
                op("17", "2", J.plusDays(9)));
        assertThat(ChargebacksService.cycle(Sens.EMISSION, J, ops, 45, J.plusDays(10)).etat()).isEqualTo("SECOND_CHARGEBACK");
        var annule = new java.util.ArrayList<>(ops);
        annule.add(op("37", "1", J.plusDays(12)));
        var c = ChargebacksService.cycle(Sens.EMISSION, J, annule, 45, J.plusDays(13));
        assertThat(c.etat()).isEqualTo("ANNULE");
        assertThat(c.lignes()).extracting(ChargebacksService.LigneCycle::etape)
                .containsExactly("Première présentation", "Couverture chargeback", "Représentation", "Second chargeback",
                        "Annulation du chargeback");
    }
}
