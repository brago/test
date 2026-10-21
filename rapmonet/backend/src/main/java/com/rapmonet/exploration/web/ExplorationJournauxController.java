package com.rapmonet.exploration.web;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.ConsultationJournaux;
import com.rapmonet.ingestion.ConsultationJournaux.Critere;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exploration/ejournal")
class ExplorationJournauxController {

    private static final int MAX_EXCEL = 200_000;
    private static final int MAX_PDF = 20_000;

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Date et heure", Type.DATE_HEURE, 9),
            new Colonne("GAB", Type.TEXTE, 6),
            new Colonne("Opération", Type.TEXTE, 5),
            new Colonne("Carte", Type.TEXTE, 9),
            new Colonne("Montant", Type.MONTANT, 7),
            new Colonne("Devise", Type.TEXTE, 3),
            new Colonne("Réponse", Type.TEXTE, 3),
            new Colonne("Libellé réponse", Type.TEXTE, 10),
            new Colonne("Statut", Type.TEXTE, 6),
            new Colonne("Compte", Type.TEXTE, 10),
            new Colonne("Référence", Type.TEXTE, 5),
            new Colonne("Billets présentés", Type.TEXTE, 6),
            new Colonne("Incidents", Type.TEXTE, 16));

    private final ConsultationJournaux consultation;
    private final JournalAudit audit;

    ExplorationJournauxController(ConsultationJournaux consultation, JournalAudit audit) {
        this.consultation = consultation;
        this.audit = audit;
    }

    @GetMapping("/operations")
    PageResultat<ConsultationJournaux.Operation> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String terminal, @RequestParam(required = false) String type,
            @RequestParam(required = false) String statut, @RequestParam(required = false) String codeReponse,
            @RequestParam(required = false) String carte, @RequestParam(required = false) String compte,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        return consultation.rechercher(new Critere(du, au, terminal, type, statut, codeReponse, carte, compte),
                Math.max(page, 0), Math.clamp(taille, 1, 500));
    }

    @GetMapping("/operations/export")
    void exporter(@RequestParam String format,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) String terminal, @RequestParam(required = false) String type,
                  @RequestParam(required = false) String statut, @RequestParam(required = false) String codeReponse,
                  @RequestParam(required = false) String carte, @RequestParam(required = false) String compte,
                  HttpServletResponse reponse) throws IOException {
        FormatExport f = ExplorationLisController.formatExport(format);
        Critere critere = new Critere(du, au, terminal, type, statut, codeReponse, carte, compte);
        long nombre = consultation.compter(critere);
        int max = f == FormatExport.PDF ? MAX_PDF : MAX_EXCEL;
        if (nombre > max) {
            throw new RegleMetierException("%d opérations : l'export %s est limité à %d lignes. Affinez les critères."
                    .formatted(nombre, f.extension().toUpperCase(), max));
        }
        DateTimeFormatter jj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> criteres = List.of(
                "Période : " + (du == null ? "début" : jj.format(du)) + " au " + (au == null ? "aujourd'hui" : jj.format(au))
                        + " · GAB : " + (terminal == null || terminal.isBlank() ? "tous" : terminal),
                "Opération : " + valeur(type, "toutes") + " · Statut : " + valeur(statut, "tous")
                        + " · Réponse : " + valeur(codeReponse, "toutes"),
                nombre + " opération(s)");
        audit.enregistrer(ActionsAudit.EXPORT, "Opérations des journaux GAB (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau tableau = new Tableau("Opérations des journaux GAB", criteres, Securite.login(), COLONNES,
                ecrire -> consultation.parcourir(critere, o -> ecrire.accept(Arrays.asList(
                        o.dateHeure(), o.terminal(), o.type().equals("RETRAIT") ? "Retrait" : "Solde", o.carte(),
                        o.montant(), o.devise(), o.codeReponse(), o.libelleReponse(), o.statut(), o.compte(), o.reference(),
                        o.billetsPresentes() == null ? null
                                : o.billetsPresentes().stream().map(String::valueOf).collect(Collectors.joining(", ")),
                        String.join(" ; ", o.incidents())))));
        ReponseExport.envoyer(reponse, tableau, f, "operations_gab");
    }

    @GetMapping("/sessions/{id}")
    ConsultationJournaux.Session session(@PathVariable long id) {
        return consultation.session(id).orElseThrow(() -> new RessourceIntrouvableException("Session introuvable : " + id));
    }

    @GetMapping("/evenements")
    List<ConsultationJournaux.Evenement> evenements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String terminal) {
        return consultation.evenements(du, au, terminal);
    }

    @GetMapping("/terminaux")
    List<String> terminaux() {
        return consultation.terminaux();
    }

    private static String valeur(String v, String defaut) {
        return v == null || v.isBlank() ? defaut : v;
    }
}
