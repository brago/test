package com.rapmonet.parametrage.web;

import com.rapmonet.parametrage.internal.ConservationService;
import com.rapmonet.parametrage.internal.ConservationService.Etat;
import com.rapmonet.parametrage.internal.ConservationService.Execution;
import com.rapmonet.parametrage.internal.ConservationService.Planification;
import com.rapmonet.securite.Securite;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Conservation des données (menu PARAMETRAGE_CONSERVATION). */
@RestController
@RequestMapping("/api/parametrage/conservation")
class ConservationController {

    record Duree(int mois, String motif) {
    }

    record DemandePlanification(boolean planifiee, int jour, String motif) {
    }

    record DemandePurge(String motif, String confirmation) {
    }

    private final ConservationService conservation;

    ConservationController(ConservationService conservation) {
        this.conservation = conservation;
    }

    @GetMapping
    Etat etat() {
        return conservation.etat(LocalDate.now());
    }

    @PutMapping("/categories/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void modifierDuree(@PathVariable String code, @RequestBody Duree d) {
        conservation.modifierDuree(code, d.mois(), d.motif(), Securite.login());
    }

    @PutMapping("/planification")
    Planification planification(@RequestBody DemandePlanification d) {
        return conservation.modifierPlanification(d.planifiee(), d.jour(), d.motif(), Securite.login());
    }

    /** La purge est irréversible : le mot PURGER doit être saisi en confirmation. */
    @PostMapping("/purges")
    Execution purger(@RequestBody DemandePurge d) {
        if (d.confirmation() == null || !d.confirmation().strip().equals("PURGER")) {
            throw new com.rapmonet.commun.web.RegleMetierException("Saisissez PURGER pour confirmer la purge (suppression définitive).");
        }
        return conservation.purger("MANUEL", d.motif(), Securite.login(), LocalDate.now());
    }
}
