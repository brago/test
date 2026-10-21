package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.LitigesLis;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
class LitigesLisService implements LitigesLis {

    private static final String SELECT = """
            select t.id, f.journee, f.nom, t.tc,
                a->>'LIS_USAGE_CODE' as usage, a->>'LIS_ACQUIRERS_REFERENCE_NUMBER' as arn,
                a->>'LIS_RETRIEVAL_REFERENCE_NUMBER' as rrn, a->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION' as carte,
                a->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION_EMPREINTE' as carte_empreinte,
                a->>'LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION' as terminal, a->>'LIS_MERCHANT_NAME' as marchand,
                a->>'LIS_TRANSACTION_DATE' as date_transaction, a->>'LIS_TRANSACTION_TIME' as heure,
                a->>'LIS_AUTHORISATION_CODE' as autorisation, a->>'LIS_DESTINATION_AMOUNT' as montant,
                a->>'LIS_DESTINATION_CURRENCY_CODE' as devise,
                a->>'LIS_REASON_CODE_FOR_CHARGEBACK_AND_REPRESENTATION' as motif,
                a->>'LIS_CHARGEBACK_REFERENCE_NUMBER' as reference_chargeback, a->>'LIS_MEMBER_MESSAGE_TEXT' as message,
                a->>'LIS_FORWARDING_INSTITUTION_IDENTIFICATION_FID' as fid,
                a->>'LIS_RECEIVING_INSTITUTION_IDENTIFICATION_RID' as rid
            from lis_transaction t join lis_fichier f on f.id = t.fichier_id, lateral (select t.attributs as a) x
            """;

    private final JdbcClient jdbc;

    LitigesLisService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OperationLitige> chargebacks() {
        return jdbc.sql(SELECT + " where t.tc in ('15', '16', '17') order by f.journee, t.id")
                .query((rs, n) -> operation(rs)).list();
    }

    @Override
    public List<OperationLitige> cycle(String arn) {
        return jdbc.sql(SELECT + " where t.attributs->>'LIS_ACQUIRERS_REFERENCE_NUMBER' = ? and t.tc between '05' and '37'"
                        + " order by f.journee, t.id")
                .param(arn).query((rs, n) -> operation(rs)).list();
    }

    private static OperationLitige operation(ResultSet rs) throws SQLException {
        return new OperationLitige(rs.getLong("id"), rs.getObject("journee", LocalDate.class), rs.getString("nom"),
                rs.getString("tc"), vide(rs.getString("usage")), vide(rs.getString("arn")), vide(rs.getString("rrn")),
                vide(rs.getString("carte")), vide(rs.getString("carte_empreinte")), vide(rs.getString("terminal")),
                vide(rs.getString("marchand")), date(rs.getString("date_transaction")), vide(rs.getString("heure")),
                vide(rs.getString("autorisation")), montant(rs.getString("montant")), vide(rs.getString("devise")),
                vide(rs.getString("motif")), vide(rs.getString("reference_chargeback")), vide(rs.getString("message")),
                vide(rs.getString("fid")), vide(rs.getString("rid")));
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static LocalDate date(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long montant(String s) {
        try {
            return s == null || s.isBlank() ? null : new BigDecimal(s).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
