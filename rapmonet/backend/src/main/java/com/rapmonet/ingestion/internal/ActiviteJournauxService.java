package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.ActiviteJournaux;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Agrégats quotidiens par GAB : sessions, opérations et passages en mode superviseur des journaux intégrés. */
@Service
@Transactional(readOnly = true)
class ActiviteJournauxService implements ActiviteJournaux {

    private static final String CAPTURE = "Carte capturée";
    private static final List<String> ANNULATIONS = List.of("Annulée par le client", "Annulée après délai dépassé", "Délai dépassé");
    private static final List<String> EMV = List.of("Refusée par la carte (GENAC 2 AAC)",
            "Pas de cryptogramme de réponse de l'émetteur (ARPC)");

    private final JdbcClient jdbc;

    ActiviteJournauxService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cumuls en cours de calcul pour un GAB et une date. */
    private static final class Cumul {
        int sessions, distribues, refuses, soldes, nonPris, captures, annulations, emv, arretes, ajouts;
        long montant;
        final Map<String, Integer> refusParCode = new TreeMap<>();
        final int[] parHeure = new int[24];
        final List<LocalTime> passages = new ArrayList<>();
    }

    @Override
    public List<JourGab> jours(LocalDate du, LocalDate au, String terminal, LocalTime debutPlage, LocalTime finPlage) {
        String gab = terminal == null || terminal.isBlank() ? null : terminal.strip();
        Map<String, Cumul> cumuls = new TreeMap<>();

        jdbc.sql("""
                        select terminal_id, date_session, heure_debut, incidents::text from ej_session
                        where date_session between ? and ? and (cast(? as varchar) is null or terminal_id = ?)""")
                .params(du, au, gab, gab)
                .query(rs -> {
                    Cumul c = cumuls.computeIfAbsent(rs.getString(1) + "|" + rs.getObject(2, LocalDate.class), k -> new Cumul());
                    c.sessions++;
                    LocalTime h = rs.getObject(3, LocalTime.class);
                    if (h != null) {
                        c.passages.add(h);
                    }
                    String incidents = rs.getString(4);
                    if (incidents.contains(CAPTURE)) {
                        c.captures++;
                    }
                    if (ANNULATIONS.stream().anyMatch(incidents::contains)) {
                        c.annulations++;
                    }
                    if (EMV.stream().anyMatch(incidents::contains)) {
                        c.emv++;
                    }
                });

        jdbc.sql("""
                        select terminal_id, cast(date_heure as date), type, statut, coalesce(code_reponse, ''),
                               coalesce(montant, 0), extract(hour from date_heure)
                        from ej_operation
                        where date_heure >= ? and date_heure < ? and (cast(? as varchar) is null or terminal_id = ?)""")
                .params(du.atStartOfDay(), au.plusDays(1).atStartOfDay(), gab, gab)
                .query(rs -> {
                    Cumul c = cumuls.computeIfAbsent(rs.getString(1) + "|" + rs.getObject(2, LocalDate.class), k -> new Cumul());
                    String type = rs.getString(3), statut = rs.getString(4), code = rs.getString(5);
                    if ("SOLDE".equals(type)) {
                        c.soldes++;
                    } else if ("DISTRIBUE".equals(statut)) {
                        c.distribues++;
                        c.montant += rs.getLong(6);
                        c.parHeure[rs.getInt(7)]++;
                    } else if ("NON_PRIS".equals(statut)) {
                        c.nonPris++;
                    } else if ("REFUSE".equals(statut)) {
                        c.refuses++;
                        c.refusParCode.merge(code.isBlank() ? "?" : code, 1, Integer::sum);
                    }
                });

        jdbc.sql("""
                        select terminal_id, cast(date_heure as date), actions::text from ej_evenement
                        where date_heure >= ? and date_heure < ? and (cast(? as varchar) is null or terminal_id = ?)""")
                .params(du.atStartOfDay(), au.plusDays(1).atStartOfDay(), gab, gab)
                .query(rs -> {
                    Cumul c = cumuls.get(rs.getString(1) + "|" + rs.getObject(2, LocalDate.class));
                    if (c != null) {
                        String actions = rs.getString(3);
                        if (actions.contains("REMISE_A_ZERO_COMPTEURS")) {
                            c.arretes++;
                        }
                        if (actions.contains("AJOUT_ESPECES")) {
                            c.ajouts++;
                        }
                    }
                });

        List<JourGab> jours = new ArrayList<>();
        cumuls.forEach((cle, c) -> {
            String[] p = cle.split("\\|");
            List<LocalTime> passages = c.passages.stream().sorted().toList();
            LocalTime[] inactivite = plusLongueInactivite(passages, debutPlage, finPlage);
            jours.add(new JourGab(p[0], LocalDate.parse(p[1]), c.sessions, c.distribues, c.montant, c.refuses,
                    new LinkedHashMap<>(c.refusParCode), c.soldes, c.nonPris, c.captures, c.annulations, c.emv,
                    Arrays.stream(c.parHeure).boxed().toList(),
                    passages.isEmpty() ? null : passages.get(0), passages.isEmpty() ? null : passages.get(passages.size() - 1),
                    inactivite == null ? 0 : (int) Duration.between(inactivite[0], inactivite[1]).toMinutes(),
                    inactivite == null ? null : inactivite[0], c.arretes, c.ajouts));
        });
        return jours;
    }

    /**
     * Plus long intervalle sans passage dans la plage horaire, bornes de la plage comprises : un GAB arrêté toute
     * la matinée donne un intervalle depuis le début de la plage.
     */
    static LocalTime[] plusLongueInactivite(List<LocalTime> passages, LocalTime debut, LocalTime fin) {
        if (debut == null || fin == null || !debut.isBefore(fin)) {
            return null;
        }
        List<LocalTime> bornes = new ArrayList<>();
        bornes.add(debut);
        passages.stream().filter(h -> h.isAfter(debut) && h.isBefore(fin)).forEach(bornes::add);
        bornes.add(fin);
        LocalTime[] meilleur = null;
        long max = -1;
        for (int i = 1; i < bornes.size(); i++) {
            long m = Duration.between(bornes.get(i - 1), bornes.get(i)).toMinutes();
            if (m > max) {
                max = m;
                meilleur = new LocalTime[] { bornes.get(i - 1), bornes.get(i) };
            }
        }
        return meilleur;
    }

    @Override
    public Map<String, String> libellesCodesReponse() {
        Map<String, String> m = new HashMap<>();
        jdbc.sql("select code, libelle from code_reponse").query(rs -> {
            m.put(rs.getString(1), rs.getString(2));
        });
        return m;
    }

    @Override
    public Map<String, LocalDate> derniersJournaux() {
        Map<String, LocalDate> m = new HashMap<>();
        jdbc.sql("select terminal_id, max(date_session) from ej_session group by terminal_id")
                .query(rs -> {
                    m.put(rs.getString(1), rs.getObject(2, LocalDate.class));
                });
        return m;
    }
}
