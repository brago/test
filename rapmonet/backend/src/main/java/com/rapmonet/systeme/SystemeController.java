package com.rapmonet.systeme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.DatabaseMetaData;

@RestController
@RequestMapping("/api/systeme")
class SystemeController {

    private final JdbcTemplate jdbc;
    private final String version;

    SystemeController(JdbcTemplate jdbc, @Value("${rapmonet.version}") String version) {
        this.jdbc = jdbc;
        this.version = version;
    }

    @GetMapping("/info")
    InfoSysteme info() {
        InfoSysteme.BaseDeDonnees base = jdbc.execute((java.sql.Connection c) -> {
            DatabaseMetaData meta = c.getMetaData();
            return new InfoSysteme.BaseDeDonnees(meta.getDatabaseProductName(), meta.getDatabaseProductVersion(), null);
        });
        String schema = jdbc.queryForObject(
                "select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1", String.class);
        return new InfoSysteme("RAPMONET", version,
                new InfoSysteme.BaseDeDonnees(base.produit(), base.version(), schema));
    }
}
