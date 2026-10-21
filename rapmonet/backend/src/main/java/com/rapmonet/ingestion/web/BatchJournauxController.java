package com.rapmonet.ingestion.web;

import com.rapmonet.ingestion.BatchJournaux;
import com.rapmonet.securite.Securite;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingestion/ejournal/batch")
class BatchJournauxController {

    private final BatchJournaux batch;

    BatchJournauxController(BatchJournaux batch) {
        this.batch = batch;
    }

    @GetMapping
    BatchJournaux.Etat etat() {
        return batch.etat();
    }

    @PutMapping("/configuration")
    BatchJournaux.Configuration modifier(@RequestBody BatchJournaux.Configuration configuration) {
        return batch.modifier(configuration, Securite.login());
    }

    /** Lance le batch immédiatement, en arrière-plan. */
    @PostMapping("/executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Long> lancer() {
        return Map.of("id", batch.lancer(Securite.login()));
    }
}
