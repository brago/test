package com.rapmonet.securite.internal;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class AuditService implements JournalAudit {

    public record Evenement(long id, OffsetDateTime horodatage, String login, String action, String objet,
                            String detail, String adresseIp, boolean succes) {
    }

    public record Critere(LocalDate du, LocalDate au, String login, String action) {
    }

    private final JdbcClient jdbc;

    AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(String action, String objet, String detail) {
        enregistrer(action, objet, detail, true);
    }

    @Override
    public void enregistrer(String action, String objet, String detail, boolean succes) {
        enregistrerPour(Securite.login(), action, objet, detail, succes);
    }

    /** Écrit dans une transaction séparée : l'événement est conservé même si l'action échoue ensuite. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrerPour(String login, String action, String objet, String detail, boolean succes) {
        jdbc.sql("""
                        insert into audit_evenement (login, action, objet, detail, adresse_ip, succes)
                        values (?, ?, ?, ?, ?, ?)""")
                .params(login, action, tronquer(objet, 200), tronquer(detail, 2000), adresseIp(), succes)
                .update();
    }

    @Transactional(readOnly = true)
    public PageResultat<Evenement> rechercher(Critere critere, int page, int taille) {
        List<Object> params = new ArrayList<>();
        String where = where(critere, params);
        long total = jdbc.sql("select count(*) from audit_evenement" + where).params(params).query(Long.class).single();
        List<Object> p = new ArrayList<>(params);
        p.add(taille);
        p.add((long) page * taille);
        List<Evenement> elements = jdbc.sql("select * from audit_evenement" + where + " order by horodatage desc, id desc limit ? offset ?")
                .params(p).query(Evenement.class).list();
        return new PageResultat<>(elements, total, page, taille);
    }

    @Transactional(readOnly = true)
    public long compter(Critere critere) {
        List<Object> params = new ArrayList<>();
        return jdbc.sql("select count(*) from audit_evenement" + where(critere, params)).params(params).query(Long.class).single();
    }

    @Transactional(readOnly = true)
    public void parcourir(Critere critere, Consumer<Evenement> consommateur) {
        List<Object> params = new ArrayList<>();
        try (var flux = jdbc.sql("select * from audit_evenement" + where(critere, params) + " order by horodatage desc, id desc")
                .params(params).query(Evenement.class).stream()) {
            flux.forEach(consommateur);
        }
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return jdbc.sql("select distinct action from audit_evenement order by action").query(String.class).list();
    }

    private static String where(Critere c, List<Object> params) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        ZoneId zone = ZoneId.systemDefault();
        if (c.du() != null) {
            w.append(" and horodatage >= ?");
            params.add(c.du().atStartOfDay(zone).toOffsetDateTime());
        }
        if (c.au() != null) {
            w.append(" and horodatage < ?");
            params.add(c.au().plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        }
        if (c.login() != null && !c.login().isBlank()) {
            w.append(" and lower(login) = lower(?)");
            params.add(c.login().strip());
        }
        if (c.action() != null && !c.action().isBlank()) {
            w.append(" and action = ?");
            params.add(c.action());
        }
        return w.toString();
    }

    private static String adresseIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest r = a.getRequest();
            return r.getRemoteAddr();
        }
        return null;
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
