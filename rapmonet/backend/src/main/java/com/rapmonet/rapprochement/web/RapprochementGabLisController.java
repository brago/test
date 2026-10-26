package com.rapmonet.rapprochement.web;

import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.rapprochement.internal.RapprochementGabLis;
import com.rapmonet.rapprochement.internal.RapprochementGabLis.Rapprochement;
import com.rapmonet.rapprochement.internal.RapprochementGabLis.Vue;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** Rapprochement journaux GAB / avis LIS des retraits confrères (menu RAPPROCHEMENT_GAB_LIS). */
@RestController
@RequestMapping("/api/rapprochement/gab-lis")
class RapprochementGabLisController {

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Résultat", Type.TEXTE, 9),
            new Colonne("GAB", Type.TEXTE, 5),
            new Colonne("Date", Type.DATE, 5),
            new Colonne("Ticket GAB", Type.DATE_HEURE, 7),
            new Colonne("Référence ticket", Type.TEXTE, 5),
            new Colonne("Avis LIS", Type.DATE_HEURE, 7),
            new Colonne("Autorisation", Type.TEXTE, 5),
            new Colonne("Réseau", Type.TEXTE, 5),
            new Colonne("Journée LIS", Type.DATE, 5),
            new Colonne("Carte", Type.TEXTE, 7),
            new Colonne("Montant", Type.MONTANT, 5),
            new Colonne("Montant LIS", Type.MONTANT, 5),
            new Colonne("Écart (min)", Type.ENTIER, 4),
            new Colonne("Traitement", Type.TEXTE, 5),
            new Colonne("Commentaire", Type.TEXTE, 10));

    record DemandeTraitement(String cle, String typeEcart, String statut, String commentaire) {
    }

    private final RapprochementGabLis service;
    private final JournalAudit audit;

    RapprochementGabLisController(RapprochementGabLis service, JournalAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    Rapprochement rapprocher(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                             @RequestParam(required = false) String terminal,
                             @RequestParam(defaultValue = "ECARTS") String vue) {
        return service.rapprocher(du, au, terminal, vue(vue));
    }

    @PostMapping("/ecarts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void traiter(@RequestBody DemandeTraitement d) {
        service.traiter(d.cle(), d.typeEcart(), d.statut(), d.commentaire(), Securite.login());
    }

    @GetMapping("/export")
    void exporter(@RequestParam String format,
                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) String terminal, @RequestParam(defaultValue = "ECARTS") String vue,
                  HttpServletResponse reponse) throws IOException {
        FormatExport f = FormatExport.depuis(format);
        Rapprochement r = service.rapprocher(du, au, terminal, vue(vue));
        DateTimeFormatter jj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String titre = "Rapprochement GAB / LIS – retraits confrères";
        List<String> criteres = List.of("Du " + jj.format(du) + " au " + jj.format(au)
                        + (terminal == null || terminal.isBlank() ? " · tous les GAB" : " · GAB " + terminal.strip()),
                "Vue : " + switch (vue(vue)) {
                    case ECARTS -> "écarts et opérations non rapprochées";
                    case RAPPROCHES -> "opérations rapprochées";
                    case TOUS -> "toutes les opérations";
                });
        audit.enregistrer(ActionsAudit.EXPORT, titre + " (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau t = new Tableau(titre, criteres, Securite.login(), COLONNES, ecrire -> r.lignes().forEach(l ->
                ecrire.accept(Arrays.asList(l.libelleResultat(), l.terminal(), l.date(), l.dateHeureJournal(),
                        l.referenceJournal(), l.dateHeureLis(), l.autorisation(), l.reseau(), l.journeeLis(), l.carte(),
                        l.montant(), l.montantLis(), l.ecartMinutes(), libelleTraitement(l.traitement()), l.commentaire()))));
        ReponseExport.envoyer(reponse, t, f, "rapprochement_gab_lis");
    }

    private static Vue vue(String v) {
        try {
            return Vue.valueOf(v.strip().toUpperCase());
        } catch (RuntimeException e) {
            throw new RegleMetierException("Vue attendue : ECARTS, RAPPROCHES ou TOUS.");
        }
    }

    private static String libelleTraitement(String t) {
        return t == null ? null : switch (t) {
            case "JUSTIFIE" -> "Justifié";
            case "REGULARISE" -> "Régularisé";
            default -> "À traiter";
        };
    }
}
