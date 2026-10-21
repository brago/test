package com.rapmonet.gab.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.BinsEmission;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Table des BIN du réseau GIM (fichier LIS_IN_BIN) : import, recherche et contrôle des BIN de la banque.
 *
 * <p>Une ligne du fichier : BIN ; début de plage ; fin de plage ; code pays ; puis des zones non utilisées,
 * séparées par des points-virgules. La plage (début et fin) fait foi.
 */
@Service
@Transactional
public class TableBinsGim {

    /** Codes pays ISO numériques des membres du GIM-UEMOA. */
    static final Map<String, String> PAYS = Map.of(
            "204", "Bénin", "384", "Côte d'Ivoire", "466", "Mali", "562", "Niger",
            "624", "Guinée-Bissau", "686", "Sénégal", "768", "Togo", "854", "Burkina Faso");

    private static final Pattern CHIFFRES = Pattern.compile("\\d+");
    private static final int MAX_ANOMALIES = 20;

    public record Plage(String prefixe, String debut, String fin, String pays, String libellePays) {
    }

    public record RapportImport(String fichier, int plages, int lignesIgnorees, List<String> anomalies) {
    }

    public record EtatTable(String fichier, OffsetDateTime importeLe, String importePar, int plages,
                            List<Repartition> parPays) {
    }

    public record Repartition(String pays, String libellePays, int plages) {
    }

    /**
     * Contrôle d'un BIN de la banque : plages GIM couvertes, pays, et autres plages du même préfixe à 6 chiffres
     * qui n'appartiennent pas à la banque (cartes indiscernables une fois masquées).
     */
    public record ControleBin(String bin, int plagesCouvertes, List<String> pays, int autresPlagesDuPrefixe,
                              List<String> paysAutresPlages) {
    }

    /** BIN vu dans les LIS émission et pas encore déclaré. */
    public record Proposition(String bin, long operations, int plagesGim, List<String> pays, boolean prefixePartage) {
    }

    private final JdbcClient jdbc;
    private final JournalAudit audit;
    private final BinsEmission emission;

    TableBinsGim(JdbcClient jdbc, JournalAudit audit, BinsEmission emission) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.emission = emission;
    }

    /** Remplace la table par le contenu du fichier. Refuse un fichier sans aucune plage valide. */
    public RapportImport importer(String fichier, InputStream contenu, String utilisateur) throws IOException {
        List<Plage> plages = new ArrayList<>();
        List<String> anomalies = new ArrayList<>();
        int ignorees = 0;
        int numero = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(contenu, StandardCharsets.ISO_8859_1))) {
            String ligne;
            while ((ligne = r.readLine()) != null) {
                numero++;
                if (ligne.isBlank()) {
                    continue;
                }
                String anomalie = null;
                String[] z = ligne.split(";", -1);
                if (z.length < 4) {
                    anomalie = "moins de 4 zones";
                } else {
                    String prefixe = z[0].strip(), debut = z[1].strip(), fin = z[2].strip(), pays = z[3].strip();
                    if (!CHIFFRES.matcher(debut).matches() || !CHIFFRES.matcher(fin).matches()
                            || debut.length() != fin.length() || debut.length() < 12 || debut.length() > 19) {
                        anomalie = "plage invalide";
                    } else if (debut.compareTo(fin) > 0) {
                        anomalie = "début de plage après la fin";
                    } else if (!CHIFFRES.matcher(pays).matches() || pays.length() > 3) {
                        anomalie = "code pays invalide";
                    } else {
                        plages.add(new Plage(prefixe.length() > 11 ? prefixe.substring(0, 11) : prefixe, debut, fin, pays, null));
                    }
                }
                if (anomalie != null) {
                    ignorees++;
                    if (anomalies.size() < MAX_ANOMALIES) {
                        anomalies.add("Ligne " + numero + " : " + anomalie);
                    }
                }
            }
        }
        if (plages.isEmpty()) {
            throw new RegleMetierException("Aucune plage de BIN reconnue : le fichier attendu est la table des BIN GIM "
                    + "(BIN;début;fin;pays;…).");
        }
        jdbc.sql("delete from bin_gim").update();
        for (Plage p : plages) {
            jdbc.sql("insert into bin_gim (prefixe, debut, fin, pays) values (?, ?, ?, ?)")
                    .params(p.prefixe(), p.debut(), p.fin(), p.pays()).update();
        }
        jdbc.sql("insert into bin_gim_import (fichier, nb_plages, importe_par) values (?, ?, ?)")
                .params(fichier, plages.size(), utilisateur).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "Table des BIN GIM " + fichier,
                plages.size() + " plages, " + ignorees + " ligne(s) ignorée(s)", true);
        return new RapportImport(fichier, plages.size(), ignorees, anomalies);
    }

    @Transactional(readOnly = true)
    public EtatTable etat() {
        List<Repartition> parPays = jdbc.sql("select pays, count(*) from bin_gim group by pays order by 2 desc, 1")
                .query((rs, n) -> new Repartition(rs.getString(1), libellePays(rs.getString(1)), rs.getInt(2))).list();
        return jdbc.sql("select fichier, importe_le, importe_par, nb_plages from bin_gim_import order by id desc limit 1")
                .query((rs, n) -> new EtatTable(rs.getString(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3),
                        rs.getInt(4), parPays))
                .optional().orElse(new EtatTable(null, null, null, 0, parPays));
    }

    /** Plages dont le numéro de début commence par la recherche, éventuellement limitées à un pays (200 au plus). */
    @Transactional(readOnly = true)
    public List<Plage> rechercher(String recherche, String pays) {
        String debut = recherche == null ? "" : recherche.strip();
        if (!debut.isEmpty() && !CHIFFRES.matcher(debut).matches()) {
            throw new RegleMetierException("La recherche porte sur les premiers chiffres de la carte.");
        }
        return jdbc.sql("""
                        select prefixe, debut, fin, pays from bin_gim
                        where debut like ? and (? = '' or pays = ?)
                        order by debut limit 200""")
                .params(debut + "%", pays == null ? "" : pays, pays == null ? "" : pays)
                .query((rs, n) -> plage(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).list();
    }

    /** Contrôle de chaque BIN déclaré par la banque face à la table GIM (vide si la table n'est pas importée). */
    @Transactional(readOnly = true)
    public List<ControleBin> controles() {
        if (jdbc.sql("select count(*) from bin_gim").query(Integer.class).single() == 0) {
            return List.of();
        }
        List<String> bins = jdbc.sql("select bin from bin_carte order by bin").query(String.class).list();
        List<ControleBin> resultat = new ArrayList<>();
        for (String bin : bins) {
            List<String> pays = jdbc.sql("select distinct pays from bin_gim where " + COUVERT + " order by 1")
                    .params(bin, bin).query(String.class).list();
            int couvertes = jdbc.sql("select count(*) from bin_gim where " + COUVERT)
                    .params(bin, bin).query(Integer.class).single();
            // Plages du même préfixe 6 chiffres qu'aucun BIN de la banque ne couvre.
            String autres = """
                    from bin_gim g where left(g.debut, 6) = left(?, 6)
                      and not exists (select 1 from bin_carte b
                                      where rpad(b.bin, length(g.debut), '0') <= g.fin
                                        and rpad(b.bin, length(g.debut), '9') >= g.debut)""";
            int nbAutres = jdbc.sql("select count(*) " + autres).param(bin).query(Integer.class).single();
            List<String> paysAutres = jdbc.sql("select distinct g.pays " + autres + " order by 1")
                    .param(bin).query(String.class).list();
            resultat.add(new ControleBin(bin, couvertes, pays.stream().map(TableBinsGim::libellePays).toList(),
                    nbAutres, paysAutres.stream().map(TableBinsGim::libellePays).toList()));
        }
        return resultat;
    }

    /** BIN vus dans les LIS émission (cartes de la banque) qui ne sont couverts par aucun BIN déclaré. */
    @Transactional(readOnly = true)
    public List<Proposition> propositions() {
        Set<String> declares = new LinkedHashSet<>(jdbc.sql("select left(bin, 6) from bin_carte").query(String.class).list());
        List<Proposition> resultat = new ArrayList<>();
        for (BinsEmission.BinVu vu : emission.binsVus()) {
            if (declares.contains(vu.bin())) {
                continue;
            }
            List<String> pays = jdbc.sql("select distinct pays from bin_gim where left(debut, 6) = ? order by 1")
                    .param(vu.bin()).query(String.class).list();
            int nb = jdbc.sql("select count(*) from bin_gim where left(debut, 6) = ?").param(vu.bin()).query(Integer.class).single();
            resultat.add(new Proposition(vu.bin(), vu.operations(), nb, pays.stream().map(TableBinsGim::libellePays).toList(),
                    nb > 1));
        }
        return resultat;
    }

    /** Une plage est couverte par un BIN si elle a au moins un numéro commençant par ce BIN. */
    private static final String COUVERT = "rpad(?, length(debut), '0') <= fin and rpad(?, length(debut), '9') >= debut";

    static String libellePays(String code) {
        return PAYS.getOrDefault(code, code);
    }

    private static Plage plage(String prefixe, String debut, String fin, String pays) {
        return new Plage(prefixe, debut, fin, pays, libellePays(pays));
    }
}
