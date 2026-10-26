package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.AchatsAcquis;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
class AchatsAcquisService implements AchatsAcquis {

    /** TC 49 dont le code commerçant n'est pas celui des GAB (6011). */
    private static final String ACHATS = "t.tc = '49' and coalesce(t.attributs->>'LIS_MERCHANT_CATEGORY_CODE', '') <> '6011'";

    private final JdbcClient jdbc;

    AchatsAcquisService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Achat> achats(LocalDate journee) {
        // Montant de la transaction en XOF : le montant destination est en devise de la carte (EUR pour une carte
        // étrangère). Les fichiers sans TRANSACTION_AMOUNT retombent sur le montant destination.
        return jdbc.sql("""
                        select t.id, f.journee, f.nom, a->>'LIS_PAYMENT_PRODUCT_INDICATOR' as ppi,
                            a->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION' as carte,
                            a->>'LIS_AUTHORISATION_CODE' as autorisation, a->>'LIS_ACQUIRERS_MICROFILM_REFERENCE_NUMBER' as arn,
                            a->>'LIS_RETRIEVAL_REFERENCE_NUMBER' as rrn, a->>'LIS_TRANSACTION_DATE' as date_transaction,
                            a->>'LIS_TRANSACTION_TIME' as heure, a->>'LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION' as terminal,
                            a->>'LIS_MERCHANT_NAME' as marchand, a->>'LIS_MERCHANT_CITY' as ville,
                            coalesce(a->>'LIS_TRANSACTION_AMOUNT', a->>'LIS_DESTINATION_AMOUNT') as montant, a->>'LIS_INTERCHANGE_FEES' as interchange
                        from lis_transaction t join lis_fichier f on f.id = t.fichier_id, lateral (select t.attributs as a) x
                        where f.journee = ?""" + " and " + ACHATS + " order by f.id, t.numero")
                .param(journee)
                .query((rs, n) -> new Achat(rs.getLong("id"), rs.getObject("journee", LocalDate.class), rs.getString("nom"),
                        reseau(rs.getString("ppi")), vide(rs.getString("carte")), vide(rs.getString("autorisation")),
                        vide(rs.getString("arn")), vide(rs.getString("rrn")), date(rs.getString("date_transaction"), journee),
                        vide(rs.getString("heure")), vide(rs.getString("terminal")), vide(rs.getString("marchand")),
                        vide(rs.getString("ville")), entier(rs.getString("montant")), decimal(rs.getString("interchange"))))
                .list();
    }

    @Override
    public List<LocalDate> journees() {
        return jdbc.sql("select distinct f.journee from lis_transaction t join lis_fichier f on f.id = t.fichier_id where "
                + ACHATS + " order by f.journee desc").query(LocalDate.class).list();
    }

    /** Indicateur de produit de paiement : 1 = VISA, 2 = Mastercard, sinon GIM (0 non spécifié, 3 carte GIM). */
    static String reseau(String ppi) {
        return switch (ppi == null ? "" : ppi.strip()) {
            case "1" -> "VISA";
            case "2" -> "MASTERCARD";
            default -> "GIM";
        };
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static LocalDate date(String s, LocalDate defaut) {
        try {
            return s == null || s.isBlank() ? defaut : LocalDate.parse(s);
        } catch (RuntimeException e) {
            return defaut;
        }
    }

    private static long entier(String s) {
        try {
            return s == null || s.isBlank() ? 0 : new BigDecimal(s).longValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal decimal(String s) {
        try {
            return s == null || s.isBlank() ? BigDecimal.ZERO : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
