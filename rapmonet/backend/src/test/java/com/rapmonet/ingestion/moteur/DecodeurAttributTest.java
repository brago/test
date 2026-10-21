package com.rapmonet.ingestion.moteur;

import com.rapmonet.parametrage.AttributFormat;
import com.rapmonet.parametrage.Pad;
import com.rapmonet.parametrage.TypeDonnee;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecodeurAttributTest {

    private static AttributFormat attribut(TypeDonnee type, int taille, int decimales) {
        return new AttributFormat("A", "A", 1, taille, Pad.ESPACE_DROITE, decimales, type, true, false);
    }

    @Test
    void alphaRetireLesEspacesDeFinEtGardeLesZeros() throws Exception {
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.ALPHA, 8, 0), "000343  ")).isEqualTo("000343");
    }

    @Test
    void numeriqueAvecOuSansDecimales() throws Exception {
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.NUMERIQUE, 12, 0), "000000050000")).isEqualTo(50000L);
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.NUMERIQUE, 12, 2), "000000050075"))
                .isEqualTo(new BigDecimal("500.75"));
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.NUMERIQUE, 4, 0), "    ")).isNull();
    }

    @Test
    void numeriqueInvalide() {
        assertThatThrownBy(() -> DecodeurAttribut.decoder(attribut(TypeDonnee.NUMERIQUE, 4, 0), "12A4"))
                .isInstanceOf(ValeurInvalideException.class);
    }

    @Test
    void dates() throws Exception {
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.DATE_JJMMAA, 6, 0), "100223")).isEqualTo(LocalDate.of(2023, 2, 10));
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.DATE_AAMMJJ, 6, 0), "230210")).isEqualTo(LocalDate.of(2023, 2, 10));
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.DATE_AAAAMMJJ, 8, 0), "20230210")).isEqualTo(LocalDate.of(2023, 2, 10));
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.DATE_JULIENNE_AAJJJ, 5, 0), "23041")).isEqualTo(LocalDate.of(2023, 2, 10));
        assertThat(DecodeurAttribut.decoder(attribut(TypeDonnee.DATE_JJMMAA, 6, 0), "000000")).isNull();
        assertThatThrownBy(() -> DecodeurAttribut.decoder(attribut(TypeDonnee.DATE_JJMMAA, 6, 0), "310223"))
                .isInstanceOf(ValeurInvalideException.class);
    }
}
