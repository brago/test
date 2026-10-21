package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.FichierLis;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.RapportIntegration;
import com.rapmonet.ingestion.RapportIntegration.AnomalieLis;
import com.rapmonet.ingestion.RapportIntegration.RapportFichier;
import com.rapmonet.ingestion.TypeLis;
import com.rapmonet.ingestion.moteur.Anomalie;
import com.rapmonet.ingestion.moteur.Gravite;
import com.rapmonet.ingestion.moteur.LecteurPositionnel;
import com.rapmonet.ingestion.moteur.ResultatLecture;
import com.rapmonet.ingestion.moteur.TransactionLue;
import com.rapmonet.parametrage.FormatFichier;
import com.rapmonet.parametrage.FormatsFichiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
class IntegrationLisService implements IntegrationLis {

    static final String FORMAT_LIS = "LIS_GIM";
    static final String ATTRIBUT_JOURNEE = "LIS_DATE_JOURNEE";

    /** Ex. LIS_IN_BACI_SW_23041_01 : type, année sur 2 chiffres, jour julien. */
    private static final Pattern NOM_FICHIER = Pattern.compile("_(SW|PR)_(\\d{2})(\\d{3})_");

    private final FormatsFichiers formats;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper json;
    private final EmpreinteCarte empreinte;

    IntegrationLisService(FormatsFichiers formats, JdbcClient jdbc, JdbcTemplate jdbcTemplate, JsonMapper json,
                          EmpreinteCarte empreinte) {
        this.empreinte = empreinte;
        this.formats = formats;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.json = json;
    }

    /** Fichier lu et contrôlé, en attente d'enregistrement. */
    private record Analyse(FichierRecu recu, String empreinte, ResultatLecture lecture, LocalDate journee,
                           List<Anomalie> anomalies) {

        boolean bloquant() {
            return anomalies.stream().anyMatch(a -> a.gravite() == Gravite.BLOQUANTE);
        }

        Map<String, Integer> repartitionTc() {
            Map<String, Integer> r = new TreeMap<>();
            lecture.transactions().forEach(t -> r.merge(t.tc(), 1, Integer::sum));
            return r;
        }
    }

    @Override
    @Transactional
    public RapportIntegration integrer(List<FichierRecu> fichiers, String utilisateur) {
        if (fichiers.isEmpty()) {
            throw new RegleMetierException("Aucun fichier à intégrer.");
        }
        FormatFichier format = formats.trouver(FORMAT_LIS)
                .orElseThrow(() -> new IllegalStateException("Format " + FORMAT_LIS + " absent du paramétrage"));
        LecteurPositionnel lecteur = new LecteurPositionnel(format, empreinte);

        List<Analyse> analyses = fichiers.stream().map(f -> analyser(f, lecteur)).toList();
        controlerCoherence(analyses);

        boolean integre = analyses.stream().noneMatch(Analyse::bloquant);
        List<RapportFichier> rapports = new ArrayList<>();
        for (Analyse a : analyses) {
            Long id = integre ? enregistrer(a, utilisateur) : null;
            rapports.add(new RapportFichier(a.recu().nom(), a.recu().typeLis(), id, a.journee(),
                    a.lecture().lignes().size(), a.lecture().transactions().size(), a.repartitionTc(),
                    a.anomalies().stream().map(IntegrationLisService::versRapport).toList()));
        }
        return new RapportIntegration(integre, rapports);
    }

    private Analyse analyser(FichierRecu recu, LecteurPositionnel lecteur) {
        String empreinte = sha256(recu.contenu());
        String texte = new String(recu.contenu(), StandardCharsets.ISO_8859_1);
        ResultatLecture lecture = lecteur.lire(List.of(texte.split("\\r?\\n", -1)));
        List<Anomalie> anomalies = new ArrayList<>(lecture.anomalies());

        jdbc.sql("select nom, integre_le from lis_fichier where empreinte_sha256 = ?").param(empreinte)
                .query((rs, n) -> "Fichier déjà intégré (« %s », le %s).".formatted(
                        rs.getString("nom"), rs.getObject("integre_le", OffsetDateTime.class).toLocalDate()))
                .optional()
                .ifPresent(m -> anomalies.addFirst(Anomalie.bloquante(null, m)));

        LocalDate journee = lecture.entete().get(ATTRIBUT_JOURNEE) instanceof LocalDate d ? d : null;
        if (journee == null) {
            anomalies.add(Anomalie.bloquante(1, "Date de journée LIS absente ou illisible dans l'en-tête."));
        } else {
            Matcher m = NOM_FICHIER.matcher(recu.nom());
            if (m.find()) {
                if (!m.group(1).equals(recu.typeLis().name())) {
                    anomalies.add(Anomalie.avertissement(null, "Le nom du fichier indique un LIS %s, il a été chargé comme LIS %s."
                            .formatted(m.group(1), recu.typeLis())));
                }
                LocalDate dateNom = LocalDate.ofYearDay(2000 + Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                if (!dateNom.equals(journee)) {
                    anomalies.add(Anomalie.avertissement(null, "Le nom du fichier indique le %s, l'en-tête le %s."
                            .formatted(dateNom, journee)));
                }
            }
            Integer existants = jdbc.sql("select count(*) from lis_fichier where journee = ? and type_lis = ?")
                    .params(journee, recu.typeLis().name()).query(Integer.class).single();
            if (existants > 0) {
                anomalies.add(Anomalie.avertissement(null, "Un autre fichier LIS %s est déjà intégré pour la journée du %s."
                        .formatted(recu.typeLis(), journee)));
            }
        }
        return new Analyse(recu, empreinte, lecture, journee, anomalies);
    }

    private static void controlerCoherence(List<Analyse> analyses) {
        long journees = analyses.stream().map(Analyse::journee).filter(java.util.Objects::nonNull).distinct().count();
        if (journees > 1) {
            analyses.forEach(a -> a.anomalies().add(Anomalie.bloquante(null,
                    "Les fichiers SW et PR ne portent pas sur la même journée.")));
        }
        long types = analyses.stream().map(a -> a.recu().typeLis()).distinct().count();
        if (types < analyses.size()) {
            analyses.forEach(a -> a.anomalies().add(Anomalie.bloquante(null, "Un seul fichier par type (SW, PR) est accepté.")));
        }
    }

    private long enregistrer(Analyse a, String utilisateur) {
        ResultatLecture lecture = a.lecture();
        int nbAvertissements = (int) a.anomalies().stream().filter(x -> x.gravite() == Gravite.AVERTISSEMENT).count();
        var cle = new GeneratedKeyHolder();
        jdbc.sql("""
                        insert into lis_fichier (nom, type_lis, format_code, journee, empreinte_sha256, entete,
                            nb_enregistrements, nb_transactions, repartition_tc, nb_avertissements, integre_par)
                        values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?)""")
                .params(a.recu().nom(), a.recu().typeLis().name(), FORMAT_LIS, a.journee(), a.empreinte(),
                        json.writeValueAsString(lecture.entete()), lecture.lignes().size(),
                        lecture.transactions().size(), json.writeValueAsString(a.repartitionTc()),
                        nbAvertissements, utilisateur)
                .update(cle, "id");
        long id = cle.getKeyAs(Long.class);

        jdbcTemplate.batchUpdate("""
                        insert into lis_transaction (fichier_id, numero, sous_fichier, tc, sequence, nb_tcr, attributs)
                        values (?, ?, ?, ?, ?, ?, ?::jsonb)""",
                lecture.transactions(), 1000, (ps, t) -> {
                    ps.setLong(1, id);
                    ps.setInt(2, t.numero());
                    ps.setString(3, t.sousFichier());
                    ps.setString(4, t.tc());
                    ps.setInt(5, t.sequence());
                    ps.setInt(6, t.lignes().size());
                    ps.setString(7, json.writeValueAsString(t.attributsSpecifiques()));
                });

        Map<Integer, Integer> transactionParLigne = lecture.transactionParLigne();
        jdbcTemplate.batchUpdate("""
                        insert into lis_enregistrement (fichier_id, sequence, tc, tcr, transaction_numero, contenu)
                        values (?, ?, ?, ?, ?, ?)""",
                lecture.lignes(), 1000, (ps, l) -> {
                    ps.setLong(1, id);
                    ps.setInt(2, l.sequence());
                    ps.setString(3, l.tc());
                    ps.setString(4, l.tcr());
                    ps.setObject(5, transactionParLigne.get(l.numero()));
                    ps.setString(6, l.contenu());
                });

        jdbcTemplate.batchUpdate("insert into lis_anomalie (fichier_id, ligne, gravite, message) values (?, ?, ?, ?)",
                a.anomalies(), 1000, (ps, x) -> {
                    ps.setLong(1, id);
                    ps.setObject(2, x.ligne());
                    ps.setString(3, x.gravite().name());
                    ps.setString(4, x.message());
                });
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichierLis> lister() {
        return jdbc.sql("select * from lis_fichier order by journee desc, type_lis desc, id desc")
                .query((rs, n) -> new FichierLis(rs.getLong("id"), rs.getString("nom"),
                        TypeLis.valueOf(rs.getString("type_lis")), rs.getObject("journee", LocalDate.class),
                        json.readValue(rs.getString("entete"), new TypeReference<Map<String, Object>>() { }),
                        rs.getInt("nb_enregistrements"), rs.getInt("nb_transactions"),
                        json.readValue(rs.getString("repartition_tc"), new TypeReference<Map<String, Integer>>() { }),
                        rs.getInt("nb_avertissements"), rs.getObject("integre_le", OffsetDateTime.class)))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnomalieLis> anomalies(long fichierId) {
        verifierExistence(fichierId);
        return jdbc.sql("select ligne, gravite, message from lis_anomalie where fichier_id = ? order by ligne nulls first, id")
                .param(fichierId)
                .query((rs, n) -> new AnomalieLis((Integer) rs.getObject("ligne"), rs.getString("gravite"), rs.getString("message")))
                .list();
    }

    @Override
    @Transactional
    public void annuler(long fichierId) {
        verifierExistence(fichierId);
        jdbc.sql("delete from lis_fichier where id = ?").param(fichierId).update();
    }

    private void verifierExistence(long fichierId) {
        if (jdbc.sql("select count(*) from lis_fichier where id = ?").param(fichierId).query(Integer.class).single() == 0) {
            throw new RessourceIntrouvableException("Fichier LIS introuvable : " + fichierId);
        }
    }

    private static AnomalieLis versRapport(Anomalie a) {
        return new AnomalieLis(a.ligne(), a.gravite().name(), a.message());
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
