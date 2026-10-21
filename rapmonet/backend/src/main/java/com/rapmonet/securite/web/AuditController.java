package com.rapmonet.securite.web;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.Securite;
import com.rapmonet.securite.internal.AuditService;
import com.rapmonet.securite.internal.AuditService.Critere;
import com.rapmonet.securite.internal.AuditService.Evenement;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/securite/audit")
class AuditController {

    private static final int MAX_LIGNES = 100_000;

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Date et heure", Type.DATE_HEURE, 9),
            new Colonne("Utilisateur", Type.TEXTE, 6),
            new Colonne("Action", Type.TEXTE, 10),
            new Colonne("Objet", Type.TEXTE, 14),
            new Colonne("Détail", Type.TEXTE, 24),
            new Colonne("Adresse IP", Type.TEXTE, 7),
            new Colonne("Résultat", Type.TEXTE, 5));

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    PageResultat<Evenement> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String login,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        return audit.rechercher(new Critere(du, au, login, action), Math.max(page, 0), Math.clamp(taille, 1, 500));
    }

    @GetMapping("/actions")
    List<String> actions() {
        return audit.actions();
    }

    @GetMapping("/export")
    void exporter(@RequestParam String format,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) String login,
                  @RequestParam(required = false) String action,
                  HttpServletResponse reponse) throws IOException {
        FormatExport f;
        try {
            f = FormatExport.depuis(format);
        } catch (IllegalArgumentException e) {
            throw new RegleMetierException(e.getMessage());
        }
        Critere critere = new Critere(du, au, login, action);
        long nombre = audit.compter(critere);
        if (nombre > MAX_LIGNES) {
            throw new RegleMetierException(nombre + " événements : l'export est limité à " + MAX_LIGNES + " lignes.");
        }
        DateTimeFormatter jj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> criteres = List.of(
                "Période : " + (du == null ? "début" : jj.format(du)) + " au " + (au == null ? "aujourd'hui" : jj.format(au)),
                "Utilisateur : " + (login == null || login.isBlank() ? "tous" : login)
                        + " · Action : " + (action == null || action.isBlank() ? "toutes" : action),
                nombre + " événement(s)");
        audit.enregistrer(ActionsAudit.EXPORT, "Piste d'audit (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau tableau = new Tableau("Piste d'audit", criteres, Securite.login(), COLONNES,
                ecrire -> audit.parcourir(critere, e -> ecrire.accept(Arrays.asList(e.horodatage(), e.login(),
                        e.action(), e.objet(), e.detail(), e.adresseIp(), e.succes() ? "Succès" : "Échec"))));
        ReponseExport.envoyer(reponse, tableau, f, "piste_audit");
    }
}
