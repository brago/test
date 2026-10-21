package com.rapmonet.ingestion.internal;

import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.IntegrationVss;
import com.rapmonet.ingestion.RapportIntegration.AnomalieLis;
import com.rapmonet.ingestion.moteur.Anomalie;
import com.rapmonet.ingestion.moteur.Gravite;
import com.rapmonet.ingestion.vss.LecteurRapportsVss;
import com.rapmonet.ingestion.vss.LigneVss;
import com.rapmonet.ingestion.vss.RapportVss;
import com.rapmonet.ingestion.vss.ResultatLectureVss;
import com.rapmonet.ingestion.vss.ValeurVss;
import com.rapmonet.parametrage.TypesRapportsVss;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
class IntegrationVssService implements IntegrationVss {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper json;
    private final TypesRapportsVss types;
    private final JournalAudit audit;

    IntegrationVssService(JdbcClient jdbc, JdbcTemplate jdbcTemplate, JsonMapper json, TypesRapportsVss types,
                          JournalAudit audit) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.json = json;
        this.types = types;
        this.audit = audit;
    }

    @Override
    @Transactional
    public Resultat integrer(String nom, byte[] contenu, String utilisateur) {
        String empreinte = sha256(contenu);
        ResultatLectureVss lecture = new LecteurRapportsVss()
                .lire(List.of(new String(contenu, StandardCharsets.ISO_8859_1).split("\\r?\\n", -1)));
        List<Anomalie> anomalies = new ArrayList<>(lecture.anomalies());

        jdbc.sql("select nom, integre_le from vss_fichier where empreinte_sha256 = ?").param(empreinte)
                .query((rs, n) -> "Fichier déjà intégré (« %s », le %s).".formatted(
                        rs.getString("nom"), rs.getObject("integre_le", OffsetDateTime.class).toLocalDate()))
                .optional().ifPresent(m -> anomalies.addFirst(Anomalie.bloquante(null, m)));

        List<RapportVss> retenus = new ArrayList<>();
        List<RapportLu> rapports = new ArrayList<>();
        for (RapportVss r : lecture.rapports()) {
            Optional<TypesRapportsVss.TypeRapportVss> type = types.trouver(r.code());
            boolean ignore = type.isPresent() && !type.get().integre();
            if (type.isEmpty()) {
                anomalies.add(Anomalie.avertissement(null, "Rapport %s (%s) non paramétré : il est enregistré tel quel."
                        .formatted(r.code(), r.titre())));
            }
            if (!ignore) {
                retenus.add(r);
            }
            rapports.add(new RapportLu(r.code(), r.titre(), r.lignes().size(), r.sansDonnees(), ignore));
        }

        LocalDate date = lecture.rapports().stream().map(RapportVss::dateTraitement).filter(Objects::nonNull)
                .findFirst().orElse(null);
        String sreId = lecture.rapports().stream().map(RapportVss::sreId).filter(Objects::nonNull).findFirst().orElse(null);
        String sreNom = lecture.rapports().stream().map(RapportVss::sreNom).filter(Objects::nonNull).findFirst().orElse(null);
        if (date == null && !lecture.rapports().isEmpty()) {
            anomalies.add(Anomalie.bloquante(null, "Date de traitement (PROC DATE) introuvable."));
        }
        if (date != null && jdbc.sql("select count(*) from vss_fichier where date_traitement = ? and sre_id is not distinct from ?")
                .params(date, sreId).query(Integer.class).single() > 0) {
            anomalies.add(Anomalie.avertissement(null, "Un autre fichier VSS est déjà intégré pour le %s (SRE %s)."
                    .formatted(date, sreId)));
        }
        SyntheseVss synthese = synthese(lecture.rapports());
        boolean integre = anomalies.stream().noneMatch(a -> a.gravite() == Gravite.BLOQUANTE);
        Long id = integre ? enregistrer(nom, empreinte, date, sreId, sreNom, synthese, retenus, anomalies, utilisateur) : null;

        audit.enregistrer(ActionsAudit.VSS_INTEGRATION, nom, integre
                ? "%s : %d rapport(s), net %s %s".formatted(date, retenus.size(), synthese.montantNet(), synthese.deviseReglement())
                : "Rejeté : " + anomalies.stream().filter(a -> a.gravite() == Gravite.BLOQUANTE).count() + " anomalie(s) bloquante(s)",
                integre);
        return new Resultat(integre, id, nom, date, sreId, sreNom, synthese, rapports,
                anomalies.stream().map(a -> new AnomalieLis(a.ligne(), a.gravite().name(), a.message())).toList());
    }

    /**
     * Montant net à régler (VSS-110 « NET SETTLEMENT AMOUNT »), nombre de transactions d'interchange
     * (VSS-110 « TOTAL INTERCHANGE VALUE ») et montant compensé (VSS-900-S « TOTAL SENT TO SETTLEMENT »).
     */
    static SyntheseVss synthese(List<RapportVss> rapports) {
        String deviseReglement = null;
        BigDecimal net = null;
        Integer nb = null;
        String deviseCompensation = null;
        BigDecimal compense = null;
        for (RapportVss r : rapports) {
            if (r.code().equals("VSS-110")) {
                for (LigneVss l : r.lignes()) {
                    if ("NET SETTLEMENT AMOUNT".equals(l.libelle()) && net == null) {
                        net = colonne(l, "TOTAL").map(ValeurVss::signe).orElse(null);
                        deviseReglement = l.deviseReglement();
                    }
                    if ("TOTAL INTERCHANGE VALUE".equals(l.libelle()) && nb == null) {
                        nb = colonne(l, "COUNT").map(v -> v.nombre().intValue()).orElse(null);
                    }
                }
            }
            if (r.code().equals("VSS-900-S") && compense == null) {
                for (LigneVss l : r.lignes()) {
                    String devise = l.deviseCompensation();
                    if ("TOTAL SENT TO SETTLEMENT".equals(l.libelle()) && devise != null
                            && !devise.equals("NONE") && !devise.equals("ALL")) {
                        compense = colonne(l, "TOTAL CLEARING AMOUNT").map(ValeurVss::signe).orElse(null);
                        deviseCompensation = devise;
                        break;
                    }
                }
            }
        }
        return new SyntheseVss(deviseReglement, net, nb, deviseCompensation, compense);
    }

    private static Optional<ValeurVss> colonne(LigneVss l, String debutNom) {
        return l.valeurs().entrySet().stream()
                .filter(e -> e.getKey().startsWith(debutNom) && e.getValue().nombre() != null)
                .map(java.util.Map.Entry::getValue).findFirst();
    }

    private long enregistrer(String nom, String empreinte, LocalDate date, String sreId, String sreNom,
                             SyntheseVss s, List<RapportVss> rapports, List<Anomalie> anomalies, String utilisateur) {
        int nbLignes = rapports.stream().mapToInt(r -> r.lignes().size()).sum();
        int nbAvertissements = (int) anomalies.stream().filter(a -> a.gravite() == Gravite.AVERTISSEMENT).count();
        var cle = new GeneratedKeyHolder();
        jdbc.sql("""
                        insert into vss_fichier (nom, empreinte_sha256, date_traitement, sre_id, sre_nom, nb_rapports, nb_lignes,
                            devise_reglement, montant_net, nb_transactions, devise_compensation, montant_compensation,
                            nb_avertissements, integre_par)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(nom, empreinte, date, sreId, sreNom, rapports.size(), nbLignes, s.deviseReglement(), s.montantNet(),
                        s.nbTransactions(), s.deviseCompensation(), s.montantCompensation(), nbAvertissements, utilisateur)
                .update(cle, "id");
        long id = cle.getKeyAs(Long.class);
        int rang = 0;
        for (RapportVss r : rapports) {
            var cleRapport = new GeneratedKeyHolder();
            jdbc.sql("""
                            insert into vss_rapport (fichier_id, rang, code, titre, sre_id, sre_nom, rollup, entite_fonds,
                                date_traitement, date_rapport, nb_pages, sans_donnees, colonnes)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""")
                    .params(id, ++rang, r.code(), r.titre(), r.sreId(), r.sreNom(), r.rollup(), r.entiteFonds(),
                            r.dateTraitement(), r.dateRapport(), r.nbPages(), r.sansDonnees(), json.writeValueAsString(r.colonnes()))
                    .update(cleRapport, "id");
            long rapportId = cleRapport.getKeyAs(Long.class);
            List<Object[]> lignes = new ArrayList<>();
            for (int i = 0; i < r.lignes().size(); i++) {
                LigneVss l = r.lignes().get(i);
                lignes.add(new Object[]{rapportId, i + 1, l.numeroLigne(), l.page(), l.niveau(), l.chemin(), l.libelle(),
                        l.deviseReglement(), l.deviseCompensation(), json.writeValueAsString(l.valeurs())});
            }
            jdbcTemplate.batchUpdate("""
                    insert into vss_ligne (rapport_id, rang, numero_ligne, page, niveau, chemin, libelle,
                        devise_reglement, devise_compensation, valeurs)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""", lignes);
        }
        jdbcTemplate.batchUpdate("insert into vss_anomalie (fichier_id, ligne, gravite, message) values (?, ?, ?, ?)",
                anomalies, 500, (ps, a) -> {
                    ps.setLong(1, id);
                    ps.setObject(2, a.ligne());
                    ps.setString(3, a.gravite().name());
                    ps.setString(4, a.message());
                });
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichierVss> lister() {
        return jdbc.sql("select * from vss_fichier order by date_traitement desc, id desc").query(this::fichier).list();
    }

    private FichierVss fichier(ResultSet rs, int n) throws SQLException {
        return new FichierVss(rs.getLong("id"), rs.getString("nom"), rs.getObject("date_traitement", LocalDate.class),
                rs.getString("sre_id"), rs.getString("sre_nom"), rs.getInt("nb_rapports"), rs.getInt("nb_lignes"),
                new SyntheseVss(rs.getString("devise_reglement"), rs.getBigDecimal("montant_net"),
                        (Integer) rs.getObject("nb_transactions"), rs.getString("devise_compensation"),
                        rs.getBigDecimal("montant_compensation")),
                rs.getInt("nb_avertissements"), rs.getObject("integre_le", OffsetDateTime.class), rs.getString("integre_par"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnomalieLis> anomalies(long fichierId) {
        existe(fichierId);
        return jdbc.sql("select ligne, gravite, message from vss_anomalie where fichier_id = ? order by ligne nulls first, id")
                .param(fichierId)
                .query((rs, n) -> new AnomalieLis((Integer) rs.getObject("ligne"), rs.getString("gravite"), rs.getString("message")))
                .list();
    }

    @Override
    @Transactional
    public void annuler(long fichierId) {
        existe(fichierId);
        String nom = jdbc.sql("select nom || ' (' || date_traitement || ')' from vss_fichier where id = ?")
                .param(fichierId).query(String.class).single();
        jdbc.sql("delete from vss_fichier where id = ?").param(fichierId).update();
        audit.enregistrer(ActionsAudit.VSS_ANNULATION, nom, null);
    }

    private void existe(long id) {
        if (jdbc.sql("select count(*) from vss_fichier where id = ?").param(id).query(Integer.class).single() == 0) {
            throw new RessourceIntrouvableException("Fichier VSS introuvable : " + id);
        }
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
