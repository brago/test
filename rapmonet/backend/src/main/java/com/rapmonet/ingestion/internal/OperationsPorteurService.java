package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.ConsultationJournaux;
import com.rapmonet.ingestion.ConsultationLis;
import com.rapmonet.ingestion.OperationsPorteur;
import com.rapmonet.parametrage.TcFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opérations d'un porteur : journaux GAB (carte masquée 6+4, compte, référence) et transactions LIS. */
@Service
@Transactional(readOnly = true)
class OperationsPorteurService implements OperationsPorteur {

    private static final String CARTE_LIS = "LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION";
    private static final List<String> REFERENCES_LIS = List.of("LIS_AUTHORISATION_CODE", "LIS_RETRIEVAL_REFERENCE_NUMBER",
            "LIS_ACQUIRERS_REFERENCE_NUMBER", "LIS_ACQUIRERS_MICROFILM_REFERENCE_NUMBER");

    private final JdbcClient jdbc;
    private final ConsultationJournauxService journaux;
    private final ConsultationLisService lis;
    private final EmpreinteCarte empreinte;

    OperationsPorteurService(JdbcClient jdbc, ConsultationJournauxService journaux, ConsultationLisService lis,
                             EmpreinteCarte empreinte) {
        this.jdbc = jdbc;
        this.journaux = journaux;
        this.lis = lis;
        this.empreinte = empreinte;
    }

    @Override
    public List<ConsultationJournaux.Operation> operationsGab(Critere c, int maximum) {
        List<Object> p = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (c.du() != null) {
            w.append(" and o.date_heure >= ?");
            p.add(c.du().atStartOfDay());
        }
        if (c.au() != null) {
            w.append(" and o.date_heure < ?");
            p.add(c.au().plusDays(1).atStartOfDay());
        }
        boolean critere = false;
        if (c.debutCarte() != null && c.finCarte() != null) {
            w.append(" and regexp_replace(coalesce(o.carte, ''), '\\D', '', 'g') = ?");
            p.add(c.debutCarte() + c.finCarte());
            critere = true;
        }
        if (vide(c.compte()) == null && vide(c.reference()) == null && !critere) {
            return List.of();
        }
        if (vide(c.compte()) != null) {
            w.append(" and o.compte = ?");
            p.add(c.compte().strip());
        }
        if (vide(c.reference()) != null) {
            w.append(" and o.reference = ?");
            p.add(c.reference().strip());
        }
        p.add(maximum);
        return jdbc.sql(ConsultationJournauxService.SELECT + w + " order by o.date_heure desc nulls last, o.id desc limit ?")
                .params(p).query((rs, n) -> journaux.operation(rs)).list();
    }

    @Override
    public List<ConsultationLis.TransactionLis> transactionsLis(Critere c, int maximum) {
        // Le compte n'existe pas dans les LIS : sans carte ni référence, rien à chercher.
        if ((c.debutCarte() == null || c.finCarte() == null) && vide(c.reference()) == null) {
            return List.of();
        }
        List<Object> p = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (c.du() != null) {
            // Journée LIS : jusqu'à 60 jours après la transaction (chargebacks, représentations).
            w.append(" and f.journee >= ?");
            p.add(c.du());
        }
        if (c.au() != null) {
            w.append(" and f.journee <= ?");
            p.add(c.au().plusDays(60));
        }
        if (c.empreinte() != null) {
            w.append(" and t.attributs ->> ? = ?");
            p.add(CARTE_LIS + "_EMPREINTE");
            p.add(c.empreinte());
        } else if (c.debutCarte() != null && c.finCarte() != null) {
            w.append(" and regexp_replace(coalesce(t.attributs ->> ?, ''), '\\D', '', 'g') = ?");
            p.add(CARTE_LIS);
            p.add(c.debutCarte() + c.finCarte());
        }
        if (vide(c.reference()) != null) {
            w.append(" and (");
            for (int i = 0; i < REFERENCES_LIS.size(); i++) {
                w.append(i == 0 ? "" : " or ").append("t.attributs ->> ? = ?");
                p.add(REFERENCES_LIS.get(i));
                p.add(c.reference().strip());
            }
            w.append(")");
        }
        p.add(maximum);
        Map<String, TcFormat> tcs = lis.format().tcs();
        return jdbc.sql(ConsultationLisService.SELECT + w + " order by f.journee desc, t.sequence limit ?")
                .params(p).query((rs, n) -> lis.transaction(rs, tcs)).list();
    }

    @Override
    public String empreinte(String numeroCarte) {
        return empreinte.apply(numeroCarte.replaceAll("\\D", ""));
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
