package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.RetraitsAcquis;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
class RetraitsAcquisService implements RetraitsAcquis {

    private final JdbcClient jdbc;

    RetraitsAcquisService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Avis> avis(LocalDate du, LocalDate au, String terminal) {
        String t = terminal == null ? "" : terminal.strip();
        // Montant de la transaction en XOF : le montant destination est en devise de la carte (EUR pour une carte
        // étrangère). Les fichiers sans TRANSACTION_AMOUNT retombent sur le montant destination.
        return jdbc.sql("""
                        select t.id, t.numero, f.journee, f.nom, a->>'LIS_PAYMENT_PRODUCT_INDICATOR' as ppi,
                            a->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION' as carte,
                            a->>'LIS_AUTHORISATION_CODE' as autorisation, a->>'LIS_RETRIEVAL_REFERENCE_NUMBER' as rrn,
                            a->>'LIS_VOUCHER_NUMBER' as bordereau, a->>'LIS_TRANSACTION_DATE' as date_transaction,
                            a->>'LIS_TRANSACTION_TIME' as heure, a->>'LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION' as terminal,
                            coalesce(a->>'LIS_TRANSACTION_AMOUNT', a->>'LIS_DESTINATION_AMOUNT') as montant
                        from lis_transaction t join lis_fichier f on f.id = t.fichier_id, lateral (select t.attributs as a) x
                        where t.tc = '49' and a->>'LIS_MERCHANT_CATEGORY_CODE' = '6011'
                          and a->>'LIS_TRANSACTION_DATE' between ? and ?
                          and (? = '' or trim(a->>'LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION') = ?)
                        order by f.id, t.numero""")
                .params(du.toString(), au.toString(), t, t)
                .query((rs, n) -> new Avis(rs.getLong("id"), rs.getString("nom") + ":" + rs.getInt("numero"),
                        rs.getObject("journee", LocalDate.class), rs.getString("nom"),
                        AchatsAcquisService.reseau(rs.getString("ppi")), vide(rs.getString("carte")),
                        vide(rs.getString("autorisation")), vide(rs.getString("rrn")), vide(rs.getString("bordereau")),
                        LocalDate.parse(rs.getString("date_transaction")), heure(rs.getString("heure")),
                        vide(rs.getString("terminal")), entier(rs.getString("montant"))))
                .list();
    }

    @Override
    public Optional<LocalDate> derniereJournee() {
        return jdbc.sql("select max(journee) from lis_fichier").query(LocalDate.class).optional();
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** HHMMSS. */
    static LocalTime heure(String s) {
        if (s == null || !s.strip().matches("\\d{6}")) {
            return null;
        }
        String h = s.strip();
        try {
            return LocalTime.of(Integer.parseInt(h.substring(0, 2)), Integer.parseInt(h.substring(2, 4)),
                    Integer.parseInt(h.substring(4, 6)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long entier(String s) {
        try {
            return s == null || s.isBlank() ? 0 : new BigDecimal(s).longValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
