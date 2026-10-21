package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.ingestion.ConsultationJournaux;
import com.rapmonet.ingestion.CoupuresGab;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
class ConsultationJournauxService implements ConsultationJournaux {

    private static final String SELECT = """
            select o.*, c.libelle as libelle_reponse, s.billets_presentes, s.billets_pris, s.incidents
            from ej_operation o
            join ej_session s on s.id = o.session_id
            left join code_reponse c on c.code = o.code_reponse
            """;
    private static final String ORDRE = " order by o.date_heure desc nulls last, o.id desc";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    private final ObjectProvider<CoupuresGab> coupuresGab;

    ConsultationJournauxService(JdbcClient jdbc, JsonMapper json, ObjectProvider<CoupuresGab> coupuresGab) {
        this.coupuresGab = coupuresGab;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public PageResultat<Operation> rechercher(Critere critere, int page, int taille) {
        List<Object> params = new ArrayList<>();
        String where = where(critere, params);
        long total = compter(where, params);
        List<Object> p = new ArrayList<>(params);
        p.add(taille);
        p.add((long) page * taille);
        return new PageResultat<>(jdbc.sql(SELECT + where + ORDRE + " limit ? offset ?").params(p)
                .query((rs, n) -> operation(rs)).list(), total, page, taille);
    }

    @Override
    public long compter(Critere critere) {
        List<Object> params = new ArrayList<>();
        return compter(where(critere, params), params);
    }

    @Override
    public void parcourir(Critere critere, Consumer<Operation> consommateur) {
        List<Object> params = new ArrayList<>();
        String where = where(critere, params);
        try (var flux = jdbc.sql(SELECT + where + ORDRE).params(params).query((rs, n) -> operation(rs)).stream()) {
            flux.forEach(consommateur);
        }
    }

    private long compter(String where, List<Object> params) {
        return jdbc.sql("select count(*) from ej_operation o" + where).params(params).query(Long.class).single();
    }

    private static String where(Critere c, List<Object> params) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if (c.du() != null) {
            w.append(" and o.date_heure >= ?");
            params.add(Timestamp.valueOf(c.du().atStartOfDay()));
        }
        if (c.au() != null) {
            w.append(" and o.date_heure < ?");
            params.add(Timestamp.valueOf(c.au().plusDays(1).atStartOfDay()));
        }
        ajouter(w, params, "o.terminal_id = ?", c.terminal());
        ajouter(w, params, "o.type = ?", c.type());
        ajouter(w, params, "o.statut = ?", c.statut());
        ajouter(w, params, "o.code_reponse = ?", c.codeReponse());
        if (c.carte() != null && !c.carte().isBlank()) {
            w.append(" and o.carte like ?");
            params.add("%" + c.carte().strip());
        }
        if (c.ecartBillets()) {
            w.append(" and o.montant_billets is distinct from o.montant and o.montant_billets is not null");
        }
        if (c.compte() != null && !c.compte().isBlank()) {
            w.append(" and o.compte like ?");
            params.add("%" + c.compte().strip() + "%");
        }
        return w.toString();
    }

    private static void ajouter(StringBuilder w, List<Object> params, String condition, String valeur) {
        if (valeur != null && !valeur.isBlank()) {
            w.append(" and ").append(condition);
            params.add(valeur);
        }
    }

    private Operation operation(ResultSet rs) throws SQLException {
        Timestamp t = rs.getTimestamp("date_heure");
        String billets = rs.getString("billets_presentes");
        return new Operation(rs.getLong("id"), rs.getLong("session_id"), rs.getString("terminal_id"), rs.getString("type"),
                rs.getString("statut"), t == null ? null : t.toLocalDateTime(), rs.getString("carte"),
                (Long) rs.getObject("montant"), rs.getString("devise"), rs.getString("code_reponse"),
                rs.getString("libelle_reponse"), rs.getString("compte"), rs.getString("reference"),
                billets == null ? null : json.readValue(billets, new TypeReference<List<Integer>>() { }),
                rs.getBoolean("billets_pris"),
                json.readValue(rs.getString("incidents"), new TypeReference<List<String>>() { }),
                (Long) rs.getObject("montant_billets"), ecart((Long) rs.getObject("montant_billets"), (Long) rs.getObject("montant")));
    }

    private static Long ecart(Long montantBillets, Long montant) {
        return montantBillets == null || montant == null ? null : montantBillets - montant;
    }

    @Override
    public Optional<Session> session(long id) {
        return jdbc.sql("select s.*, f.nom from ej_session s join ej_fichier f on f.id = s.fichier_id where s.id = ?")
                .param(id)
                .query((rs, n) -> {
                    String billets = rs.getString("billets_presentes");
                    String compteurs = rs.getString("compteurs");
                    return new Session(rs.getLong("id"), rs.getString("nom"), rs.getString("terminal_id"),
                            rs.getObject("date_session", LocalDate.class), rs.getObject("heure_debut", LocalTime.class),
                            rs.getString("carte"),
                            billets == null ? null : json.readValue(billets, new TypeReference<List<Integer>>() { }),
                            rs.getBoolean("billets_pris"),
                            compteurs == null ? null : json.readValue(compteurs, new TypeReference<Map<String, Object>>() { }),
                            json.readValue(rs.getString("incidents"), new TypeReference<List<String>>() { }),
                            rs.getBoolean("complete"), rs.getString("texte"),
                            jdbc.sql(SELECT + " where o.session_id = ? order by o.numero_ligne").param(id)
                                    .query((r, k) -> operation(r)).list(),
                            coupures(rs.getString("terminal_id")));
                }).optional();
    }

    private List<Long> coupures(String terminal) {
        CoupuresGab gab = coupuresGab.getIfAvailable();
        var duGab = gab == null ? java.util.Optional.<List<Long>>empty() : gab.coupuresParType(terminal);
        if (duGab.isPresent()) {
            return duGab.get();
        }
        return jdbc.sql("select valeur from parametre_application where cle = 'ejournal.coupures'").query(String.class)
                .optional().map(IntegrationJournauxService::lireCoupures).orElse(null);
    }

    @Override
    public int joursCouverts(String terminal, LocalDate du, LocalDate au) {
        return jdbc.sql("select count(distinct date_session) from ej_session where terminal_id = ? and date_session between ? and ?")
                .params(terminal, du, au).query(Integer.class).single();
    }

    @Override
    public long montantDistribue(String terminal, LocalDateTime apres, LocalDateTime jusqua) {
        return jdbc.sql("""
                        select coalesce(sum(coalesce(montant_billets, montant)), 0) from ej_operation
                        where terminal_id = ? and type = 'RETRAIT' and statut = 'DISTRIBUE' and date_heure > ? and date_heure <= ?""")
                .params(terminal, Timestamp.valueOf(apres), Timestamp.valueOf(jusqua)).query(Long.class).single();
    }

    @Override
    public List<Evenement> evenements(LocalDate du, LocalDate au, String terminal) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (du != null) {
            w.append(" and date_heure >= ?");
            params.add(Timestamp.valueOf(du.atStartOfDay()));
        }
        if (au != null) {
            w.append(" and date_heure < ?");
            params.add(Timestamp.valueOf(au.plusDays(1).atStartOfDay()));
        }
        ajouter(w, params, "terminal_id = ?", terminal);
        return jdbc.sql("select * from ej_evenement" + w + " order by date_heure desc nulls last limit 500").params(params)
                .query((rs, n) -> {
                    Timestamp t = rs.getTimestamp("date_heure");
                    return new Evenement(rs.getLong("id"), rs.getString("terminal_id"),
                            t == null ? null : t.toLocalDateTime(),
                            json.readValue(rs.getString("actions"), new TypeReference<List<String>>() { }),
                            json.readValue(rs.getString("releves"), new TypeReference<List<Map<String, List<Integer>>>>() { }),
                            json.readValue(rs.getString("especes_ajoutees"), new TypeReference<Map<String, Integer>>() { }),
                            rs.getString("texte"));
                }).list();
    }

    @Override
    public List<String> terminaux() {
        return jdbc.sql("select distinct terminal_id from ej_fichier order by 1").query(String.class).list();
    }

}
