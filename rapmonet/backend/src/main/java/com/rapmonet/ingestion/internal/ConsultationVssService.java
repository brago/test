package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.ConsultationVss;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
class ConsultationVssService implements ConsultationVss {

    private static final String SELECT = """
            select r.id, r.fichier_id, f.nom as nom_fichier, r.code, t.libelle as libelle_type, r.titre,
                   r.date_traitement, r.sre_id, r.sre_nom, r.nb_pages, r.sans_donnees, r.rollup, r.entite_fonds,
                   r.colonnes, (select count(*) from vss_ligne l where l.rapport_id = r.id) as nb_lignes
            from vss_rapport r
            join vss_fichier f on f.id = r.fichier_id
            left join vss_type_rapport t on t.code = r.code
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ConsultationVssService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<ResumeRapport> rechercher(LocalDate du, LocalDate au, String code) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (du != null) {
            where.append(" and r.date_traitement >= ?");
            params.add(du);
        }
        if (au != null) {
            where.append(" and r.date_traitement <= ?");
            params.add(au);
        }
        if (code != null && !code.isBlank()) {
            where.append(" and r.code = ?");
            params.add(code);
        }
        return jdbc.sql(SELECT + where + " order by r.date_traitement desc, r.fichier_id desc, r.rang limit 1000")
                .params(params).query((rs, n) -> resume(rs)).list();
    }

    @Override
    public Optional<Rapport> rapport(long id) {
        return jdbc.sql(SELECT + " where r.id = ?").param(id).query((rs, n) -> new Rapport(resume(rs),
                rs.getString("rollup"), rs.getString("entite_fonds"),
                json.readValue(rs.getString("colonnes"), new TypeReference<List<String>>() { }), lignes(id))).optional();
    }

    private List<Ligne> lignes(long rapportId) {
        return jdbc.sql("select * from vss_ligne where rapport_id = ? order by rang").param(rapportId)
                .query((rs, n) -> new Ligne(rs.getInt("rang"), rs.getInt("page"), rs.getInt("niveau"), rs.getString("chemin"),
                        rs.getString("libelle"), rs.getString("devise_reglement"), rs.getString("devise_compensation"),
                        new LinkedHashMap<>(json.readValue(rs.getString("valeurs"), new TypeReference<Map<String, Valeur>>() { }))))
                .list();
    }

    private static ResumeRapport resume(ResultSet rs) throws SQLException {
        return new ResumeRapport(rs.getLong("id"), rs.getLong("fichier_id"), rs.getString("nom_fichier"), rs.getString("code"),
                rs.getString("libelle_type"), rs.getString("titre"), rs.getObject("date_traitement", LocalDate.class),
                rs.getString("sre_id"), rs.getString("sre_nom"), rs.getInt("nb_pages"), rs.getBoolean("sans_donnees"),
                rs.getInt("nb_lignes"));
    }
}
