package com.rapmonet.ingestion;

import com.rapmonet.ingestion.FichierLisSynthetique.AvisAcquisition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Montants des TC 49 en XOF : le GIM envoie les montants sans décimales (constaté sur des fichiers réels) et, pour une
 * carte étrangère, le montant destination est dans la devise de la carte (centimes d'euro).
 */
@SpringBootTest
@ActiveProfiles("test")
class MontantsLisIntegrationTest {

    private static final LocalDate JOURNEE = LocalDate.of(2026, 4, 10);

    @Autowired
    IntegrationLis lis;

    @Autowired
    AchatsAcquis achats;

    @Autowired
    RetraitsAcquis retraits;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void vider() {
        jdbc.update("delete from lis_fichier");
        jdbc.update("delete from chargeback_dossier");
    }

    @Test
    void lesMontantsTc49SontEnXofEtNonEnDeviseDeLaCarte() {
        byte[] pr = FichierLisSynthetique.creer("100426", "001", List.of(), List.of(
                // Carte VISA étrangère : 36 500 XOF, soit 55,64 EUR côté porteur.
                new AvisAcquisition("4000000000000003", 36500, "01000001", "5812", "090426", "RESTAURANT", "1", 183,
                        "120000", "123456", "978", 5564),
                AvisAcquisition.retrait("4000000000000005", 100000, "99990001", "090426", "101500", "654321")));
        lis.integrer(List.of(new IntegrationLis.FichierRecu("LIS_IN_TEST_PR_26100_01", TypeLis.PR, pr)), "test");

        var achat = achats.achats(JOURNEE).getFirst();
        assertThat(achat.montant()).isEqualTo(36500);
        assertThat(achat.interchange()).isEqualByComparingTo(new BigDecimal("183"));
        var retrait = retraits.avis(LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 9), "99990001").getFirst();
        assertThat(retrait.montant()).isEqualTo(100000);

        // Valeurs stockées (exploration LIS) : sans décimales.
        assertThat(jdbc.queryForObject("select (attributs->>'LIS_TRANSACTION_AMOUNT')::numeric from lis_transaction "
                + "where tc = '49' and attributs->>'LIS_MERCHANT_CATEGORY_CODE' = '6011'", BigDecimal.class))
                .isEqualByComparingTo("100000");
    }

    @Test
    void laMigrationEstTraceeDansLHistoriqueDuFormat() {
        assertThat(jdbc.queryForObject("select decimales from fmt_attribut where code = 'LIS_TRANSACTION_AMOUNT'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from fmt_attribut where code like 'LIS\\_%' and decimales > 0",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from fmt_modification where modifie_par = 'migration V26'",
                Integer.class)).isEqualTo(19);
    }
}
