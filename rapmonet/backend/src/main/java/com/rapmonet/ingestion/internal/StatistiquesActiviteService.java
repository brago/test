package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.StatistiquesActivite;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
class StatistiquesActiviteService implements StatistiquesActivite {

    /** Date de la transaction (à défaut, journée du fichier) et montant, protégés contre les valeurs mal formées. */
    private static final String LIS = """
            with t as (
              select %s as entite, a->>'LIS_MERCHANT_NAME' as libelle,
                case t.sous_fichier when 'FL' then 'GIM' when 'FV' then 'VISA' when 'FM' then 'MASTERCARD' else t.sous_fichier end as reseau,
                case when a->>'LIS_TRANSACTION_DATE' ~ '^\\d{4}-\\d{2}-\\d{2}$' then (a->>'LIS_TRANSACTION_DATE')::date
                     else f.journee end as jour,
                case when a->>'LIS_DESTINATION_AMOUNT' ~ '^-?\\d+(\\.\\d+)?$' then (a->>'LIS_DESTINATION_AMOUNT')::numeric
                     else 0 end as montant
              from lis_transaction t join lis_fichier f on f.id = t.fichier_id, lateral (select t.attributs as a) x
              where %s)
            select entite, max(libelle) as libelle, reseau, to_char(jour, 'YYYY-MM') as mois, count(*) as nombre,
                   coalesce(sum(montant), 0)::bigint as montant
            from t where jour between ? and ? and entite is not null and entite <> ''
            group by entite, reseau, to_char(jour, 'YYYY-MM')
            """;

    private static final String JOURNAUX = """
            select %s as entite, null as libelle, null as reseau, to_char(date_heure, 'YYYY-MM') as mois, count(*) as nombre,
                   coalesce(sum(coalesce(montant_billets, montant)), 0) as montant
            from ej_operation
            where type = 'RETRAIT' and statut = 'DISTRIBUE' and date_heure >= ? and date_heure < ?
            group by 1, to_char(date_heure, 'YYYY-MM')
            """;

    private static final String TERMINAL = "a->>'LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION'";
    private static final String BIN = "left(a->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION', 6)";

    private final JdbcClient jdbc;

    StatistiquesActiviteService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Agregat> agreger(Source source, LocalDate du, LocalDate au) {
        String sql = switch (source) {
            case LIS_ACQUISITION_RETRAITS -> LIS.formatted(TERMINAL,
                    "t.tc = '49' and a->>'LIS_MERCHANT_CATEGORY_CODE' = '6011'");
            case LIS_ACQUISITION_ACHATS -> LIS.formatted(TERMINAL,
                    "t.tc = '49' and coalesce(a->>'LIS_MERCHANT_CATEGORY_CODE', '') <> '6011'");
            case LIS_EMISSION_RETRAITS -> LIS.formatted(BIN,
                    "f.type_lis = 'SW' and t.tc = '07' and a->>'LIS_USAGE_CODE' = '1'");
            case LIS_EMISSION_ACHATS -> LIS.formatted(BIN,
                    "f.type_lis = 'SW' and t.tc = '05' and a->>'LIS_USAGE_CODE' = '1'");
            case JOURNAUX_RETRAITS -> JOURNAUX.formatted("terminal_id");
            case JOURNAUX_RETRAITS_PAR_BIN -> JOURNAUX.formatted("left(carte, 6)");
        };
        boolean journaux = source == Source.JOURNAUX_RETRAITS || source == Source.JOURNAUX_RETRAITS_PAR_BIN;
        Object debut = journaux ? du.atStartOfDay() : du;
        Object fin = journaux ? au.plusDays(1).atStartOfDay() : au;
        return jdbc.sql(sql).params(debut, fin)
                .query((rs, n) -> new Agregat(rs.getString("entite"), rs.getString("libelle"), rs.getString("reseau"),
                        YearMonth.parse(rs.getString("mois")), rs.getLong("nombre"), rs.getLong("montant")))
                .list();
    }
}
