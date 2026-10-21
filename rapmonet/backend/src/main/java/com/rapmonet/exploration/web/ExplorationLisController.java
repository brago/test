package com.rapmonet.exploration.web;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.ConsultationLis;
import com.rapmonet.ingestion.ConsultationLis.TransactionLis;
import com.rapmonet.ingestion.TypeLis;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/exploration/lis/transactions")
class ExplorationLisController {

    private static final int TAILLE_MAX = 500;
    static final int MAX_LIGNES_EXCEL = 200_000;
    static final int MAX_LIGNES_PDF = 20_000;

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Journée", Type.DATE, 6),
            new Colonne("LIS", Type.TEXTE, 3),
            new Colonne("S/F", Type.TEXTE, 3),
            new Colonne("TC", Type.TEXTE, 3),
            new Colonne("Libellé TC", Type.TEXTE, 12),
            new Colonne("Séquence", Type.ENTIER, 5),
            new Colonne("Date", Type.DATE, 6),
            new Colonne("Montant", Type.MONTANT, 8),
            new Colonne("Carte", Type.TEXTE, 11),
            new Colonne("Commerçant / GAB", Type.TEXTE, 14),
            new Colonne("Terminal", Type.TEXTE, 7),
            new Colonne("Autorisation", Type.TEXTE, 6),
            new Colonne("Référence (ARN / RRN)", Type.TEXTE, 14));

    private final ConsultationLis consultation;
    private final JournalAudit audit;

    ExplorationLisController(ConsultationLis consultation, JournalAudit audit) {
        this.consultation = consultation;
        this.audit = audit;
    }

    @GetMapping
    PageResultat<TransactionLis> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) TypeLis typeLis,
            @RequestParam(required = false) String tc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        return consultation.rechercher(new ConsultationLis.Critere(du, au, typeLis, tc),
                Math.max(page, 0), Math.clamp(taille, 1, TAILLE_MAX));
    }

    /** Export Excel (xlsx) ou PDF des transactions correspondant aux critères. */
    @GetMapping("/export")
    void exporter(@RequestParam String format,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) TypeLis typeLis,
                  @RequestParam(required = false) String tc,
                  HttpServletResponse reponse) throws IOException {
        FormatExport f = formatExport(format);
        ConsultationLis.Critere critere = new ConsultationLis.Critere(du, au, typeLis, tc);
        long nombre = consultation.compter(critere);
        int max = f == FormatExport.PDF ? MAX_LIGNES_PDF : MAX_LIGNES_EXCEL;
        if (nombre > max) {
            throw new RegleMetierException("%d transactions : l'export %s est limité à %d lignes. Affinez les critères."
                    .formatted(nombre, f.extension().toUpperCase(), max));
        }
        Tableau tableau = new Tableau("Transactions LIS", criteres(critere, nombre), Securite.login(), COLONNES,
                ecrire -> consultation.parcourir(critere, t -> ecrire.accept(ligne(t))));
        audit.enregistrer(ActionsAudit.EXPORT, "Transactions LIS (" + f.extension() + ")",
                String.join(" ; ", criteres(critere, nombre)));
        ReponseExport.envoyer(reponse, tableau, f, "transactions_lis");
    }

    @GetMapping("/{id}")
    ConsultationLis.DetailTransaction detail(@PathVariable long id) {
        return consultation.detail(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Transaction LIS introuvable : " + id));
    }

    private static List<Object> ligne(TransactionLis t) {
        var s = t.synthese();
        return Arrays.asList(t.journee(), t.typeLis().name(), t.sousFichier(), t.tc(), t.libelleTc(), t.sequence(),
                s.date(), s.montant(), s.carte(), s.libelle(), s.terminal(), s.autorisation(), s.reference());
    }

    private static List<String> criteres(ConsultationLis.Critere c, long nombre) {
        DateTimeFormatter jj = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> l = new ArrayList<>();
        l.add("Période : " + (c.du() == null ? "début" : jj.format(c.du())) + " au "
                + (c.au() == null ? "aujourd'hui" : jj.format(c.au())));
        l.add("Type de LIS : " + (c.typeLis() == null ? "tous" : c.typeLis()) + " · TC : "
                + (c.tc() == null || c.tc().isBlank() ? "tous" : c.tc()));
        l.add(nombre + " transaction(s)");
        return l;
    }

    static FormatExport formatExport(String format) {
        try {
            return FormatExport.depuis(format);
        } catch (IllegalArgumentException e) {
            throw new RegleMetierException(e.getMessage());
        }
    }
}
