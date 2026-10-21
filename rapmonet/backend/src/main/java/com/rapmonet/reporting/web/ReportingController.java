package com.rapmonet.reporting.web;

import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.reporting.internal.IndicateursService;
import com.rapmonet.reporting.internal.IndicateursService.DescriptionIndicateur;
import com.rapmonet.reporting.internal.IndicateursService.EtatTrimestriel;
import com.rapmonet.reporting.internal.IndicateursService.Kpi;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import com.rapmonet.securite.Securite;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Indicateurs (menu REPORTING_KPI) et base des états réglementaires (menu REPORTING_REGLEMENTAIRE). */
@RestController
@RequestMapping("/api/reporting")
class ReportingController {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRANCE);

    private final IndicateursService service;
    private final JournalAudit audit;

    ReportingController(IndicateursService service, JournalAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/kpi/indicateurs")
    List<DescriptionIndicateur> indicateurs() {
        return service.indicateurs();
    }

    @GetMapping("/kpi")
    Kpi kpi(@RequestParam String indicateur, @RequestParam YearMonth du, @RequestParam YearMonth au) {
        return service.kpi(indicateur, du, au);
    }

    /** Tableau croisé (entités × mois) en Excel ou PDF ; mesure NOMBRE ou MONTANT. */
    @GetMapping("/kpi/export")
    void exporterKpi(@RequestParam String format, @RequestParam String indicateur, @RequestParam YearMonth du,
                     @RequestParam YearMonth au, @RequestParam(defaultValue = "MONTANT") String mesure,
                     HttpServletResponse reponse) throws IOException {
        Kpi k = service.kpi(indicateur, du, au);
        boolean montant = !"NOMBRE".equals(mesure);
        FormatExport f = FormatExport.depuis(format);
        List<Colonne> colonnes = new ArrayList<>(List.of(new Colonne(k.axe() == IndicateursService.Axe.CARTE ? "BIN" : "Terminal",
                Type.TEXTE, 5), new Colonne("Libellé", Type.TEXTE, 9)));
        k.mois().forEach(m -> colonnes.add(new Colonne(MOIS.format(m), montant ? Type.MONTANT : Type.ENTIER, 5)));
        colonnes.add(new Colonne("Total", montant ? Type.MONTANT : Type.ENTIER, 6));
        List<String> criteres = List.of(k.description(), "Du " + MOIS.format(du) + " au " + MOIS.format(au) + " · "
                + (montant ? "Montants (XOF)" : "Nombre d'opérations") + " · " + k.lignes().size() + " entité(s)");
        audit.enregistrer(ActionsAudit.EXPORT, "KPI " + k.libelle() + " (" + f.extension() + ")", criteres.get(1));
        Tableau t = new Tableau(k.libelle(), criteres, Securite.login(), colonnes, ecrire -> {
            for (var l : k.lignes()) {
                List<Object> v = new ArrayList<>(Arrays.asList(l.entite(), l.libelle()));
                v.addAll(montant ? l.montants() : l.nombres());
                v.add(montant ? l.totalMontant() : l.totalNombre());
                ecrire.accept(v);
            }
            List<Object> total = new ArrayList<>(Arrays.asList("Total", ""));
            total.addAll(montant ? k.totauxMontant() : k.totauxNombre());
            total.add(montant ? k.totalMontant() : k.totalNombre());
            ecrire.accept(total);
        });
        ReponseExport.envoyer(reponse, t, f, "kpi_" + indicateur.toLowerCase());
    }

    @GetMapping("/reglementaire/trimestre")
    EtatTrimestriel trimestre(@RequestParam int annee, @RequestParam int trimestre) {
        return service.trimestre(annee, trimestre);
    }

    @GetMapping("/reglementaire/trimestre/export")
    void exporterTrimestre(@RequestParam String format, @RequestParam int annee, @RequestParam int trimestre,
                           HttpServletResponse reponse) throws IOException {
        EtatTrimestriel e = service.trimestre(annee, trimestre);
        FormatExport f = FormatExport.depuis(format);
        String titre = "Activité monétique – T" + trimestre + " " + annee;
        List<String> criteres = List.of("Du " + e.du().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " au "
                + e.au().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), e.avertissement());
        audit.enregistrer(ActionsAudit.EXPORT, titre + " (" + f.extension() + ")", criteres.get(0));
        Tableau t = new Tableau(titre, criteres, Securite.login(), List.of(
                new Colonne("Section", Type.TEXTE, 7), new Colonne("Rubrique", Type.TEXTE, 10),
                new Colonne("Réseau", Type.TEXTE, 4), new Colonne("Nombre", Type.ENTIER, 4),
                new Colonne("Montant (XOF)", Type.MONTANT, 5), new Colonne("GAB / TPE / BIN", Type.ENTIER, 4)),
                ecrire -> e.lignes().forEach(l -> ecrire.accept(Arrays.asList(l.section(), l.rubrique(), l.reseau(),
                        l.nombre(), l.montant(), l.entites()))));
        ReponseExport.envoyer(reponse, t, f, "activite_T" + trimestre + "_" + annee);
    }
}
