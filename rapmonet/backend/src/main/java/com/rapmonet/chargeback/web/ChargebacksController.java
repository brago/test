package com.rapmonet.chargeback.web;

import com.rapmonet.chargeback.internal.ChargebacksService;
import com.rapmonet.chargeback.internal.ChargebacksService.Critere;
import com.rapmonet.chargeback.internal.ChargebacksService.Dossier;
import com.rapmonet.chargeback.internal.ChargebacksService.JournalGab;
import com.rapmonet.chargeback.internal.ChargebacksService.Sens;
import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Suivi des chargebacks : /api/chargebacks/emission (menu CHARGEBACK_EMISSION) et /acquisition (CHARGEBACK_ACQUISITION). */
@RestController
@RequestMapping("/api/chargebacks/{sens}")
class ChargebacksController {

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Journée chargeback", Type.DATE, 5),
            new Colonne("Type", Type.TEXTE, 6),
            new Colonne("Carte", Type.TEXTE, 7),
            new Colonne("RRN", Type.TEXTE, 6),
            new Colonne("ARN", Type.TEXTE, 10),
            new Colonne("Autorisation", Type.TEXTE, 4),
            new Colonne("Date transaction", Type.DATE, 5),
            new Colonne("Terminal", Type.TEXTE, 5),
            new Colonne("Montant", Type.MONTANT, 5),
            new Colonne("Motif", Type.TEXTE, 3),
            new Colonne("Situation", Type.TEXTE, 9),
            new Colonne("Jours", Type.ENTIER, 3),
            new Colonne("Régularisé", Type.TEXTE, 4),
            new Colonne("Commentaire", Type.TEXTE, 9));

    private final ChargebacksService service;
    private final JournalAudit audit;

    ChargebacksController(ChargebacksService service, JournalAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    private static Sens sens(String valeur) {
        return switch (valeur) {
            case "emission" -> Sens.EMISSION;
            case "acquisition" -> Sens.ACQUISITION;
            default -> throw new RessourceIntrouvableException("Sens inconnu : " + valeur);
        };
    }

    @GetMapping
    Map<String, Object> rechercher(@PathVariable String sens,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                                   @RequestParam(required = false) String reference,
                                   @RequestParam(required = false) String regularisation,
                                   @RequestParam(defaultValue = "false") boolean archives,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        PageResultat<Dossier> r = service.rechercher(new Critere(sens(sens), du, au, reference, regularisation, archives),
                page, Math.min(taille, 500));
        return Map.of("page", r, "binsParametres", service.binsParametres());
    }

    @GetMapping("/{id}")
    Dossier dossier(@PathVariable String sens, @PathVariable long id) {
        return service.dossier(sens(sens), id);
    }

    @PostMapping("/{id}/regularisation")
    Dossier regulariser(@PathVariable String sens, @PathVariable long id, @RequestBody Map<String, String> corps) {
        return service.regulariser(sens(sens), id, corps.getOrDefault("statut", ""), corps.get("commentaire"), Securite.login());
    }

    @PostMapping("/archivage")
    Map<String, Integer> archiver(@PathVariable String sens) {
        return Map.of("archives", service.archiver(sens(sens), Securite.login()));
    }

    @GetMapping("/{id}/journal-gab")
    JournalGab journalGab(@PathVariable String sens, @PathVariable long id) {
        if (sens(sens) != Sens.ACQUISITION) {
            throw new RessourceIntrouvableException("Le journal GAB ne concerne que les chargebacks reçus (acquisition).");
        }
        return service.journalGab(id);
    }

    @GetMapping("/export")
    void exporter(@PathVariable String sens, @RequestParam String format,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) String reference, @RequestParam(required = false) String regularisation,
                  @RequestParam(defaultValue = "false") boolean archives, HttpServletResponse reponse) throws IOException {
        Sens s = sens(sens);
        FormatExport f = FormatExport.depuis(format);
        DateTimeFormatter jj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String titre = "Suivi des chargebacks " + (s == Sens.EMISSION ? "émission" : "acquisition");
        List<String> criteres = List.of(reference != null && !reference.isBlank() ? "ARN / RRN : " + reference
                        : "Journées du " + (du == null ? "début" : jj.format(du)) + " au " + (au == null ? "aujourd'hui" : jj.format(au)),
                "Régularisation : " + (regularisation == null || regularisation.isBlank() ? "toutes" : regularisation)
                        + (archives ? " · archives comprises" : ""));
        audit.enregistrer(ActionsAudit.EXPORT, titre + " (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau t = new Tableau(titre, criteres, Securite.login(), COLONNES,
                ecrire -> service.parcourir(new Critere(s, du, au, reference, regularisation, archives),
                        d -> ecrire.accept(Arrays.asList(d.journeeChargeback(), d.libelleTc(), d.carte(), d.rrn(), d.arn(),
                                d.autorisation(), d.dateTransaction(), d.terminal(), d.montant(), d.motif(), d.situation(),
                                d.joursEcoules(), d.regularisation(), d.commentaire()))));
        ReponseExport.envoyer(reponse, t, f, "chargebacks_" + sens);
    }
}
