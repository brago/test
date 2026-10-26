package com.rapmonet.rapprochement.web;

import com.rapmonet.rapprochement.internal.RapprochementGabLis;
import com.rapmonet.rapprochement.internal.RapprochementGabLis.Simulation;
import com.rapmonet.rapprochement.internal.ReglesGabLis;
import com.rapmonet.rapprochement.internal.ReglesGabLis.Modification;
import com.rapmonet.rapprochement.internal.ReglesGabLis.Regles;
import com.rapmonet.securite.Securite;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Règles du rapprochement GAB ↔ LIS : consultation, simulation et modification motivée (menu RAPPROCHEMENT_REGLES). */
@RestController
@RequestMapping("/api/rapprochement/regles")
class ReglesRapprochementController {

    record DemandeModification(Regles regles, String motif) {
    }

    record DemandeSimulation(Regles regles, LocalDate du, LocalDate au, String terminal) {
    }

    private final ReglesGabLis regles;
    private final RapprochementGabLis rapprochement;

    ReglesRapprochementController(ReglesGabLis regles, RapprochementGabLis rapprochement) {
        this.regles = regles;
        this.rapprochement = rapprochement;
    }

    @GetMapping("/gab-lis")
    Regles lire() {
        return regles.lire();
    }

    @PutMapping("/gab-lis")
    Regles modifier(@RequestBody DemandeModification d) {
        return regles.modifier(d.regles(), d.motif(), Securite.login());
    }

    @PostMapping("/gab-lis/simulation")
    Simulation simuler(@RequestBody DemandeSimulation d) {
        return rapprochement.simuler(d.du(), d.au(), d.terminal(), d.regles());
    }

    @GetMapping("/gab-lis/historique")
    List<Modification> historique() {
        return regles.historique();
    }
}
