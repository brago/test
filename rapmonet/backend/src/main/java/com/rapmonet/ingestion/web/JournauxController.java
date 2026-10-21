package com.rapmonet.ingestion.web;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.IntegrationJournaux;
import com.rapmonet.ingestion.RapportIntegration;
import com.rapmonet.securite.Securite;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/ingestion/ejournal")
class JournauxController {

    private final IntegrationJournaux integration;

    JournauxController(IntegrationJournaux integration) {
        this.integration = integration;
    }

    /** Intègre un ou plusieurs journaux ; chaque fichier est intégré ou rejeté indépendamment. */
    @PostMapping(consumes = "multipart/form-data")
    List<IntegrationJournaux.Resultat> integrer(@RequestPart("fichiers") List<MultipartFile> fichiers) throws IOException {
        if (fichiers == null || fichiers.isEmpty()) {
            throw new RegleMetierException("Sélectionnez au moins un journal.");
        }
        List<IntegrationJournaux.Resultat> resultats = new ArrayList<>();
        for (MultipartFile f : fichiers) {
            resultats.add(integration.integrer(f.getOriginalFilename(), f.getBytes(), Securite.login()));
        }
        return resultats;
    }

    @GetMapping
    List<IntegrationJournaux.FichierJournal> lister() {
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
