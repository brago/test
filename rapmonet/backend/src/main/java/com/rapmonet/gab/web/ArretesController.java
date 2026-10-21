package com.rapmonet.gab.web;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.gab.internal.ArretesService;
import com.rapmonet.gab.internal.ArretesService.Arrete;
import com.rapmonet.gab.internal.ArretesService.Critere;
import com.rapmonet.gab.internal.ArretesService.Preparation;
import com.rapmonet.gab.internal.ArretesService.Saisie;
import com.rapmonet.gab.internal.RapportsGab;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gab/arretes")
class ArretesController {

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Référence", Type.TEXTE, 4),
            new Colonne("Date arrêté", Type.DATE_HEURE, 6),
            new Colonne("GAB", Type.TEXTE, 4),
            new Colonne("Nom du GAB", Type.TEXTE, 7),
            new Colonne("Gabiste", Type.TEXTE, 6),
            new Colonne("Appro", Type.TEXTE, 4),
            new Colonne("(0) Appro", Type.MONTANT, 6),
            new Colonne("(5) Journal", Type.MONTANT, 6),
            new Colonne("(6) Physique", Type.MONTANT, 6),
            new Colonne("Écart (6)-(5)", Type.MONTANT, 5),
            new Colonne("(8) Comptable", Type.MONTANT, 6),
            new Colonne("Écart comptable", Type.MONTANT, 5),
            new Colonne("Statut", Type.TEXTE, 4),
            new Colonne("Saisi par", Type.TEXTE, 4),
            new Colonne("Validé / annulé par", Type.TEXTE, 4));

    private final ArretesService service;
    private final JournalAudit audit;

    ArretesController(ArretesService service, JournalAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    PageResultat<Arrete> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String terminal, @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        return service.rechercher(new Critere(du, au, terminal, statut), page, Math.min(taille, 500));
    }

    @GetMapping("/{id}")
    Arrete arrete(@PathVariable long id) {
        return service.arrete(id);
    }

    /** Appros à arrêter, cassettes et compteurs lus dans le journal (passage du gabiste le plus proche de la date). */
    @GetMapping("/preparation")
    Preparation preparation(@RequestParam String terminal,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date) {
        return service.preparation(terminal, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Arrete creer(@RequestBody Saisie saisie) {
        return service.creer(saisie, Securite.login());
    }

    @PutMapping("/{id}")
    Arrete modifier(@PathVariable long id, @RequestBody Saisie saisie) {
        return service.modifier(id, saisie, Securite.login());
    }

    @PostMapping("/{id}/validation")
    Arrete valider(@PathVariable long id) {
        return service.valider(id, Securite.login());
    }

    @PostMapping("/{id}/annulation")
    Arrete annuler(@PathVariable long id, @RequestBody Map<String, String> corps) {
        return service.annuler(id, corps.get("motif"), Securite.login());
    }

    @GetMapping("/{id}/rapport")
    ResponseEntity<byte[]> rapport(@PathVariable long id) {
        Arrete a = service.arrete(id);
        audit.enregistrer(ActionsAudit.EXPORT, "Rapport arrêté " + a.reference(), "PDF");
        return Pdf.reponse(RapportsGab.arrete(a, Securite.login()), "arrete_" + a.terminal() + "_" + a.reference() + ".pdf");
    }

    @GetMapping("/export")
    void exporter(@RequestParam String format,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) String terminal, @RequestParam(required = false) String statut,
                  HttpServletResponse reponse) throws IOException {
        FormatExport f = FormatExport.depuis(format);
        Critere c = new Critere(du, au, terminal, statut);
        List<String> criteres = List.of(Criteres.periode(du, au, terminal) + " · Statut : " + Criteres.statut(statut));
        audit.enregistrer(ActionsAudit.EXPORT, "Historique des arrêtés GAB (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau t = new Tableau("Historique des arrêtés GAB", criteres, Securite.login(), COLONNES,
                ecrire -> service.parcourir(c, a -> ecrire.accept(Arrays.asList(a.reference(), a.dateArrete(), a.terminal(),
                        a.libelleGab(), a.gabiste(), a.approReference(), a.montantAppro(), a.montantJournal(),
                        a.montantPhysique(), a.ecartPhysiqueJournal(), a.soldeComptable(), a.ecartPhysiqueComptable(),
                        Criteres.statut(a.statut()), a.saisiPar(), a.decidePar()))));
        ReponseExport.envoyer(reponse, t, f, "arretes_gab");
    }
}
