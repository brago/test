package com.rapmonet.ingestion.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InactiviteGabTest {

    private static final LocalTime DEBUT = LocalTime.of(7, 0), FIN = LocalTime.of(21, 0);

    @Test
    void plusLongIntervalleDansLaPlageBornesComprises() {
        // GAB arrêté toute la matinée : l'intervalle part du début de la plage.
        assertThat(ActiviteJournauxService.plusLongueInactivite(List.of(LocalTime.of(13, 0), LocalTime.of(14, 0),
                LocalTime.of(20, 0)), DEBUT, FIN)).containsExactly(DEBUT, LocalTime.of(13, 0));
        // Les passages hors plage ne comptent pas.
        assertThat(ActiviteJournauxService.plusLongueInactivite(List.of(LocalTime.of(2, 0), LocalTime.of(8, 0),
                LocalTime.of(9, 0), LocalTime.of(17, 30)), DEBUT, FIN)).containsExactly(LocalTime.of(9, 0), LocalTime.of(17, 30));
        // Aucun passage : toute la plage.
        assertThat(ActiviteJournauxService.plusLongueInactivite(List.of(), DEBUT, FIN)).containsExactly(DEBUT, FIN);
    }
}
