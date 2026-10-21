package com.rapmonet.exploration.web;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.ConsultationLis;
import com.rapmonet.ingestion.TypeLis;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/exploration/lis/transactions")
class ExplorationLisController {

    private static final int TAILLE_MAX = 500;

    private final ConsultationLis consultation;

    ExplorationLisController(ConsultationLis consultation) {
        this.consultation = consultation;
    }

    @GetMapping
    PageResultat<ConsultationLis.TransactionLis> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) TypeLis typeLis,
            @RequestParam(required = false) String tc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        return consultation.rechercher(new ConsultationLis.Critere(du, au, typeLis, tc),
                Math.max(page, 0), Math.clamp(taille, 1, TAILLE_MAX));
    }

    @GetMapping("/{id}")
    ConsultationLis.DetailTransaction detail(@PathVariable long id) {
        return consultation.detail(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Transaction LIS introuvable : " + id));
    }
}
