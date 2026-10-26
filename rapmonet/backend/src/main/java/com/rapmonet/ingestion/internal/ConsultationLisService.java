package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.ingestion.ConsultationLis;
import com.rapmonet.ingestion.TypeLis;
import com.rapmonet.ingestion.moteur.DecodeurAttribut;
import com.rapmonet.ingestion.moteur.ValeurInvalideException;
import com.rapmonet.parametrage.AttributFormat;
import com.rapmonet.parametrage.FormatFichier;
import com.rapmonet.parametrage.FormatsFichiers;
import com.rapmonet.parametrage.TcFormat;
import com.rapmonet.parametrage.Zone;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
class ConsultationLisService implements ConsultationLis {

    static final String SELECT = """
            select t.id, f.journee, f.type_lis, f.nom, t.sous_fichier, t.tc, t.sequence, t.nb_tcr, t.attributs,
                   t.fichier_id, t.numero
            from lis_transaction t join lis_fichier f on f.id = t.fichier_id
            """;

    private final JdbcClient jdbc;
    private final FormatsFichiers formats;
    private final JsonMapper json;

    ConsultationLisService(JdbcClient jdbc, FormatsFichiers formats, JsonMapper json) {
        this.jdbc = jdbc;
        this.formats = formats;
        this.json = json;
    }

    private static final String ORDRE = " order by f.journee desc, f.type_lis desc, t.sequence";

    @Override
    public PageResultat<TransactionLis> rechercher(Critere critere, int page, int taille) {
        List<Object> params = new ArrayList<>();
        String where = where(critere, params);
        long total = compter(where, params);
        Map<String, TcFormat> tcs = format().tcs();
        List<Object> paramsPage = new ArrayList<>(params);
        paramsPage.add(taille);
        paramsPage.add((long) page * taille);
        List<TransactionLis> elements = jdbc.sql(SELECT + where + ORDRE + " limit ? offset ?")
                .params(paramsPage).query((rs, n) -> transaction(rs, tcs)).list();
        return new PageResultat<>(elements, total, page, taille);
    }

    @Override
    public long compter(Critere critere) {
        List<Object> params = new ArrayList<>();
        return compter(where(critere, params), params);
    }

    @Override
    public void parcourir(Critere critere, Consumer<TransactionLis> consommateur) {
        List<Object> params = new ArrayList<>();
        String where = where(critere, params);
        Map<String, TcFormat> tcs = format().tcs();
        try (var flux = jdbc.sql(SELECT + where + ORDRE).params(params).query((rs, n) -> transaction(rs, tcs)).stream()) {
            flux.forEach(consommateur);
        }
    }

    private long compter(String where, List<Object> params) {
        return jdbc.sql("select count(*) from lis_transaction t join lis_fichier f on f.id = t.fichier_id" + where)
                .params(params).query(Long.class).single();
    }

    private static String where(Critere critere, List<Object> params) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (critere.du() != null) {
            where.append(" and f.journee >= ?");
            params.add(critere.du());
        }
        if (critere.au() != null) {
            where.append(" and f.journee <= ?");
            params.add(critere.au());
        }
        if (critere.typeLis() != null) {
            where.append(" and f.type_lis = ?");
            params.add(critere.typeLis().name());
        }
        if (critere.tc() != null && !critere.tc().isBlank()) {
            where.append(" and t.tc = ?");
            params.add(critere.tc());
        }
        return where.toString();
    }

    @Override
    public Optional<DetailTransaction> detail(long transactionId) {
        FormatFichier format = format();
        record Ligne(TransactionLis transaction, long fichierId, int numero) {
        }
        return jdbc.sql(SELECT + " where t.id = ?").param(transactionId)
                .query((rs, n) -> new Ligne(transaction(rs, format.tcs()), rs.getLong("fichier_id"), rs.getInt("numero")))
                .optional()
                .map(l -> new DetailTransaction(l.transaction(), lignes(format, l.fichierId(), l.numero())));
    }

    private List<LigneDetail> lignes(FormatFichier format, long fichierId, int numero) {
        return jdbc.sql("""
                        select sequence, tc, tcr, contenu from lis_enregistrement
                        where fichier_id = ? and transaction_numero = ? order by sequence""")
                .params(fichierId, numero)
                .query((rs, n) -> {
                    String tc = rs.getString("tc");
                    String tcr = rs.getString("tcr");
                    String contenu = rs.getString("contenu");
                    List<ValeurAttribut> valeurs = format.tc(tc).flatMap(t -> t.tcr(tcr))
                            .map(f -> f.attributs().stream().map(a -> valeur(a, contenu)).toList())
                            .orElse(List.of());
                    return new LigneDetail(rs.getInt("sequence"), tcr, contenu, valeurs);
                }).list();
    }

    private static ValeurAttribut valeur(AttributFormat a, String contenu) {
        String brut = new Zone(a.position(), a.taille()).extraire(contenu);
        try {
            return new ValeurAttribut(a.code(), a.libelle(), a.position(), a.taille(), brut,
                    DecodeurAttribut.decoder(a, brut), null);
        } catch (ValeurInvalideException e) {
            return new ValeurAttribut(a.code(), a.libelle(), a.position(), a.taille(), brut, null, e.getMessage());
        }
    }

    TransactionLis transaction(ResultSet rs, Map<String, TcFormat> tcs) throws SQLException {
        String tc = rs.getString("tc");
        Map<String, Object> attributs = json.readValue(rs.getString("attributs"), new TypeReference<Map<String, Object>>() { });
        return new TransactionLis(rs.getLong("id"), rs.getObject("journee", LocalDate.class),
                TypeLis.valueOf(rs.getString("type_lis")), rs.getString("nom"), rs.getString("sous_fichier"),
                tc, tcs.containsKey(tc) ? tcs.get(tc).libelle() : null, rs.getInt("sequence"), rs.getInt("nb_tcr"),
                synthese(attributs), attributs);
    }

    /** Attributs candidats pour chaque valeur de synthèse, par ordre de priorité. */
    private static final List<String> DATE = List.of("LIS_TRANSACTION_DATE", "LIS_SETTLEMENT_DATE", "LIS_REMITTANCE_DEPOSIT_DATE_6");
    private static final List<String> MONTANT = List.of("LIS_SOURCE_AMOUNT", "LIS_TRANSACTION_AMOUNT",
            "LIS_NET_AMOUNT_IN_CFA", "LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12");
    private static final List<String> CARTE = List.of("LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION");
    private static final List<String> LIBELLE = List.of("LIS_MERCHANT_NAME", "LIS_MERCHANT_ACRONYM", "LIS_TYPE_OF_REPORTING");
    private static final List<String> TERMINAL = List.of("LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION", "LIS_TERMINAL_NUMBER");
    private static final List<String> AUTORISATION = List.of("LIS_AUTHORISATION_CODE");
    private static final List<String> REFERENCE = List.of("LIS_ACQUIRERS_REFERENCE_NUMBER",
            "LIS_ACQUIRERS_MICROFILM_REFERENCE_NUMBER", "LIS_RETRIEVAL_REFERENCE_NUMBER");

    static Synthese synthese(Map<String, Object> a) {
        Object date = premier(a, DATE);
        Object montant = premier(a, MONTANT);
        return new Synthese(date instanceof String s ? LocalDate.parse(s) : null,
                montant instanceof Number n ? n : null,
                texte(a, CARTE), texte(a, LIBELLE), texte(a, TERMINAL), texte(a, AUTORISATION), texte(a, REFERENCE));
    }

    private static Object premier(Map<String, Object> a, List<String> cles) {
        for (String c : cles) {
            Object v = a.get(c);
            if (v != null && !(v instanceof String s && s.isBlank())) {
                return v;
            }
        }
        return null;
    }

    private static String texte(Map<String, Object> a, List<String> cles) {
        Object v = premier(a, cles);
        return v == null ? null : v.toString();
    }

    FormatFichier format() {
        return formats.trouver(IntegrationLisService.FORMAT_LIS)
                .orElseThrow(() -> new IllegalStateException("Format LIS absent du paramétrage"));
    }
}
