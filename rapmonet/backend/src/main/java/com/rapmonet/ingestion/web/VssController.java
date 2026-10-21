package com.rapmonet.ingestion.web;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.IntegrationVss;
import com.rapmonet.ingestion.RapportIntegration;
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
import java.util.List;

@RestController
@RequestMapping("/api/ingestion/vss")
class VssController {

    private final IntegrationVss integration;

    VssController(IntegrationVss integration) {
        this.integration = integration;
    }

    /** Intègre un fichier de rapports VSS (VX). Réponse 200 si intégré, 422 si rejeté. */
    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<IntegrationVss.Resultat> integrer(@RequestPart("fichier") MultipartFile fichier) throws IOException {
        if (fichier == null || fichier.isEmpty()) {
            throw new RegleMetierException("Sélectionnez un fichier VSS (VX).");
        }
        IntegrationVss.Resultat r = integration.integrer(fichier.getOriginalFilename(), fichier.getBytes(), Securite.login());
        return ResponseEntity.status(r.integre() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT).body(r);
    }

    @GetMapping
    List<IntegrationVss.FichierVss> lister() {
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
