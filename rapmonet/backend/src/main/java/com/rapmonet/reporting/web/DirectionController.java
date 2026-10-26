package com.rapmonet.reporting.web;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.reporting.internal.TableauBordDirection;
import com.rapmonet.reporting.internal.TableauBordDirection.Tableau;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Tableau de bord de la direction (menu REPORTING_DIRECTION). */
@RestController
@RequestMapping("/api/reporting/direction")
class DirectionController {

    private final TableauBordDirection tableau;
    private final JournalAudit audit;

    DirectionController(TableauBordDirection tableau, JournalAudit audit) {
        this.tableau = tableau;
        this.audit = audit;
    }

    /** Mois au format AAAA-MM ; par défaut le mois en cours. */
    @GetMapping
    Tableau tableau(@RequestParam(required = false) String mois) {
        YearMonth m;
        try {
            m = mois == null || mois.isBlank() ? YearMonth.now() : YearMonth.parse(mois.strip());
        } catch (DateTimeParseException e) {
            throw new RegleMetierException("Mois attendu au format AAAA-MM.");
        }
        if (m.isAfter(YearMonth.now())) {
            throw new RegleMetierException("Le mois ne peut pas être dans le futur.");
        }
        return tableau.tableau(m, LocalDate.now());
    }

    /** Trace l'impression du tableau de bord (le PDF est produit par le navigateur). */
    @org.springframework.web.bind.annotation.PostMapping("/impression")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void impression(@RequestParam String mois) {
        audit.enregistrer(ActionsAudit.EXPORT, "Tableau de bord direction (impression)", "Mois " + mois);
    }
}
