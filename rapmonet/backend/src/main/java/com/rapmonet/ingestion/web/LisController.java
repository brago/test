package com.rapmonet.ingestion.web;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.FichierLis;
import com.rapmonet.ingestion.IntegrationLis;
import com.rapmonet.ingestion.RapportIntegration;
import com.rapmonet.ingestion.TypeLis;
import com.rapmonet.securite.Securite;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ingestion/lis")
class LisController {

    private final IntegrationLis integration;

    LisController(IntegrationLis integration) {
        this.integration = integration;
    }

    /**
     * Intègre le LIS SW et/ou le LIS PR d'une journée.
     * Réponse 200 si intégré, 422 si rejeté (le rapport détaille les anomalies).
     */
    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<RapportIntegration> integrer(@RequestPart(name = "sw", required = false) MultipartFile sw,
                                                @RequestPart(name = "pr", required = false) MultipartFile pr)
            throws IOException {
        List<IntegrationLis.FichierRecu> fichiers = new ArrayList<>();
        if (sw != null && !sw.isEmpty()) {
            fichiers.add(new IntegrationLis.FichierRecu(sw.getOriginalFilename(), TypeLis.SW, sw.getBytes()));
        }
        if (pr != null && !pr.isEmpty()) {
            fichiers.add(new IntegrationLis.FichierRecu(pr.getOriginalFilename(), TypeLis.PR, pr.getBytes()));
        }
        if (fichiers.isEmpty()) {
            throw new RegleMetierException("Sélectionnez au moins un fichier LIS (SW ou PR).");
        }
        RapportIntegration rapport = integration.integrer(fichiers, Securite.login());
        return ResponseEntity.status(rapport.integre() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT).body(rapport);
    }

    @GetMapping
    List<FichierLis> lister() {
        return integration.lister();
    }

    @GetMapping("/{id}/anomalies")
    List<RapportIntegration.AnomalieLis> anomalies(@PathVariable long id) {
        return integration.anomalies(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annuler(@PathVariable long id) {
        integration.annuler(id);
    }
}
