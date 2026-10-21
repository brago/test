package com.rapmonet.compense.web;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.compense.internal.CommercantsService;
import com.rapmonet.compense.internal.CommercantsService.Commercant;
import com.rapmonet.compense.internal.CommercantsService.Parametres;
import com.rapmonet.compense.internal.CommercantsService.RapportImport;
import com.rapmonet.compense.internal.CompenseService;
import com.rapmonet.compense.internal.CompenseService.Apercu;
import com.rapmonet.compense.internal.CompenseService.Traitement;
import com.rapmonet.compense.internal.ExportCompense;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Compense TPE : /commercants et /parametres (menu COMPENSE_COMMERCANTS), /traitements (COMPENSE_TRAITEMENT). */
@RestController
@RequestMapping("/api/compense")
class CompenseController {

    private final CommercantsService commercants;
    private final CompenseService compense;
    private final JournalAudit audit;

    CompenseController(CommercantsService commercants, CompenseService compense, JournalAudit audit) {
        this.commercants = commercants;
        this.compense = compense;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ référentiel

    @GetMapping("/commercants")
    List<Commercant> commercants() {
        return commercants.commercants();
    }

    @PutMapping("/commercants/{terminal}")
    Commercant enregistrer(@PathVariable String terminal, @RequestBody Commercant c) {
        return commercants.enregistrer(new Commercant(terminal, c.nom(), c.compte(), c.tauxGim(), c.tauxVisa(), c.tauxMci(),
                c.actif()), Securite.login());
    }

    @DeleteMapping("/commercants/{terminal}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimer(@PathVariable String terminal) {
        commercants.supprimer(terminal, Securite.login());
    }

    /** Import de la feuille « N° COMPTES » d'un classeur Excel (xlsx ou xlsm). */
    @PostMapping("/commercants/import")
    RapportImport importer(@RequestParam("fichier") MultipartFile fichier) throws IOException {
        if (fichier.isEmpty()) {
            throw new RegleMetierException("Fichier vide.");
        }
        return commercants.importer(fichier.getBytes(), fichier.getOriginalFilename(), Securite.login());
    }

    @GetMapping("/parametres")
    Parametres parametres() {
        return commercants.parametres();
    }

    @PutMapping("/parametres")
    Parametres modifierParametres(@RequestBody Parametres p) {
        return commercants.modifierParametres(p, Securite.login());
    }

    // ------------------------------------------------------------------ traitements

    @GetMapping("/traitements/journees")
    List<LocalDate> journees() {
        return compense.journees();
    }

    @GetMapping("/traitements/apercu")
    Apercu apercu(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee) {
        return compense.apercu(journee);
    }

    @PostMapping("/traitements")
    @ResponseStatus(HttpStatus.CREATED)
    Traitement creer(@RequestBody Map<String, String> corps) {
        String j = corps.get("journee");
        if (j == null || j.isBlank()) {
            throw new RegleMetierException("La journée est obligatoire.");
        }
        return compense.creer(LocalDate.parse(j), Securite.login());
    }

    @GetMapping("/traitements")
    List<Traitement> traitements(@RequestParam(required = false) String statut) {
        return compense.traitements(statut);
    }

    @GetMapping("/traitements/{id}")
    Traitement traitement(@PathVariable long id) {
        return compense.traitement(id);
    }

    @PostMapping("/traitements/{id}/validation")
    Traitement valider(@PathVariable long id) {
        return compense.valider(id, Securite.login());
    }

    @PostMapping("/traitements/{id}/annulation")
    Traitement annuler(@PathVariable long id, @RequestBody Map<String, String> corps) {
        return compense.annuler(id, corps.get("motif"), Securite.login());
    }

    /** Classeur Excel : pièces par réseau, MACRO (import Amplitude), RÉCAP. Réservé aux traitements validés. */
    @GetMapping("/traitements/{id}/excel")
    ResponseEntity<byte[]> excel(@PathVariable long id, @RequestParam(defaultValue = "false") boolean brouillon) {
        Traitement t = compense.traitement(id);
        if (t.statut().equals("ANNULE")) {
            throw new RegleMetierException("Ce traitement est annulé.");
        }
        if (t.statut().equals("SAISI") && !brouillon) {
            throw new RegleMetierException("Le traitement doit être validé avant de produire la macro comptable "
                    + "(un brouillon peut être téléchargé pour contrôle).");
        }
        audit.enregistrer(ActionsAudit.EXPORT, "Compense TPE " + t.reference() + (t.statut().equals("SAISI") ? " (brouillon)" : ""),
                "Excel : " + t.ecritures().size() + " écriture(s)");
        String nom = "COMPENSE_TPE_" + t.journee() + "_" + t.reference() + (t.statut().equals("SAISI") ? "_BROUILLON" : "") + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExportCompense.excel(t));
    }
}
