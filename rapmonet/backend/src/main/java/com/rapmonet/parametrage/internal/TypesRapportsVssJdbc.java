package com.rapmonet.parametrage.internal;

import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.parametrage.TypesRapportsVss;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
class TypesRapportsVssJdbc implements TypesRapportsVss {

    private final JdbcClient jdbc;
    private final JournalAudit audit;

    TypesRapportsVssJdbc(JdbcClient jdbc, JournalAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeRapportVss> lister() {
        return jdbc.sql("select code, libelle, description, integre from vss_type_rapport order by rang")
                .query(TypeRapportVss.class).list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TypeRapportVss> trouver(String code) {
        return jdbc.sql("select code, libelle, description, integre from vss_type_rapport where code = ?")
                .param(code).query(TypeRapportVss.class).optional();
    }

    @Override
    @Transactional
    public TypeRapportVss modifierIntegration(String code, boolean integre) {
        trouver(code).orElseThrow(() -> new RessourceIntrouvableException("Type de rapport VSS inconnu : " + code));
        jdbc.sql("update vss_type_rapport set integre = ? where code = ?").params(integre, code).update();
        audit.enregistrer(ActionsAudit.PARAMETRAGE_VSS, code, integre ? "Rapport intégré" : "Rapport ignoré");
        return trouver(code).orElseThrow();
    }
}
