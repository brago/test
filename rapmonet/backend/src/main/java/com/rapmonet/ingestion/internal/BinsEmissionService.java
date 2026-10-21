package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.BinsEmission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class BinsEmissionService implements BinsEmission {

    private final JdbcClient jdbc;

    BinsEmissionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Fichiers SW : opérations des cartes de la banque chez les autres ; TC 05 à 07 avec code usage 1 = présentation. */
    @Override
    public List<BinVu> binsVus() {
        return jdbc.sql("""
                        select left(t.attributs->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION', 6) as bin, count(*) as nb
                        from lis_transaction t join lis_fichier f on f.id = t.fichier_id
                        where f.type_lis = 'SW' and t.tc in ('05', '06', '07')
                          and coalesce(t.attributs->>'LIS_USAGE_CODE', '') = '1'
                          and t.attributs->>'LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION' ~ '^[0-9]{6}'
                        group by 1 order by 2 desc, 1""")
                .query((rs, n) -> new BinVu(rs.getString("bin"), rs.getLong("nb"))).list();
    }
}
