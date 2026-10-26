package com.rapmonet.ingestion.internal;

import com.rapmonet.ingestion.BatchJournaux.Configuration;
import com.rapmonet.ingestion.BatchLis;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.RapportIntegration;
import com.rapmonet.ingestion.TypeLis;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Batch des fichiers LIS : chaque fichier du dossier de dépôt est intégré seul, son type (SW ou PR) lu dans son nom
 * (LIS_IN_BANQUE_SW_AAJJJ_NN…). Archive par mois de journée.
 */
@Service
public class BatchLisService extends BatchDossier implements BatchLis {

    static final String TRAITEMENT = "LIS";
    private static final Pattern TYPE = Pattern.compile("(?:^|_)(SW|PR)_\\d{5}");

    private final IntegrationLis integration;

    BatchLisService(JdbcClient jdbc, JsonMapper json, IntegrationLis integration, JournalAudit audit,
                    @Qualifier("applicationTaskExecutor") TaskExecutor executeur,
                    @Value("${rapmonet.batch.delai-stabilite-secondes:120}") long delaiStabilite,
                    @Value("${rapmonet.batch.fuseau-horaire:}") String fuseau) {
        super(jdbc, json, audit, executeur, delaiStabilite, fuseau);
        this.integration = integration;
    }

    @Override
    String traitement() {
        return TRAITEMENT;
    }

    @Override
    String prefixe() {
        return "lis.batch";
    }

    @Override
    String libelle() {
        return "fichiers LIS";
    }

    @Override
    String libelleFichier() {
        return "fichier LIS";
    }

    @Override
    Configuration parDefaut() {
        return new Configuration(true, "08:00", "donnees/lis/entree", "donnees/lis/archive", "donnees/lis/rejet");
    }

    @Override
    String actionAudit() {
        return ActionsAudit.BATCH_LIS;
    }

    @Override
    String actionAuditParametrage() {
        return ActionsAudit.PARAMETRAGE_BATCH;
    }

    @Override
    Traitement integrer(String nom, byte[] contenu) {
        Matcher m = TYPE.matcher(nom.toUpperCase());
        if (!m.find()) {
            String motif = "Type SW ou PR introuvable dans le nom du fichier (attendu : LIS_IN_<BANQUE>_SW_<AAJJJ>_<NN>).";
            return new Traitement("REJETE", null, 0, 0, motif, List.of("BLOQUANTE : " + motif), null);
        }
        TypeLis type = TypeLis.valueOf(m.group(1));
        RapportIntegration r = integration.integrer(List.of(new IntegrationLis.FichierRecu(nom, type, contenu)),
                UTILISATEUR_BATCH);
        RapportIntegration.RapportFichier f = r.fichiers().getFirst();
        List<RapportIntegration.AnomalieLis> bloquantes = f.anomalies().stream()
                .filter(a -> a.gravite().equals("BLOQUANTE")).toList();
        boolean dejaIntegre = !r.integre() && bloquantes.stream().anyMatch(a -> a.message().contains("déjà intégré"));
        String resultat = r.integre() ? "INTEGRE" : dejaIntegre ? "DEJA_INTEGRE" : "REJETE";
        String message = r.integre()
                ? (f.anomalies().isEmpty() ? null : f.anomalies().size() + " avertissement(s)")
                : bloquantes.stream().map(RapportIntegration.AnomalieLis::message).collect(Collectors.joining(" ; "));
        LocalDate journee = f.journee() != null ? f.journee() : LocalDate.now(fuseau);
        return new Traitement(resultat, type + " " + journee, f.nbTransactions(), 0, message,
                f.anomalies().stream().map(a -> a.gravite() + (a.ligne() == null ? "" : " ligne " + a.ligne()) + " : "
                        + a.message()).toList(),
                Path.of(journee.toString().substring(0, 7)));
    }
}
