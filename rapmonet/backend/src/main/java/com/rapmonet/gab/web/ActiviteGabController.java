package com.rapmonet.gab.web;

import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.gab.internal.ActiviteGab;
import com.rapmonet.gab.internal.ActiviteGab.Activite;
import com.rapmonet.gab.internal.ActiviteGab.DetailGab;
import com.rapmonet.gab.internal.ActiviteGab.Signalement;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Activité des GAB (menu GAB_ACTIVITE). */
@RestController
@RequestMapping("/api/gab/activite")
class ActiviteGabController {

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("GAB", Type.TEXTE, 5), new Colonne("Libellé", Type.TEXTE, 10), new Colonne("Agence", Type.TEXTE, 5),
            new Colonne("Jours avec journal", Type.ENTIER, 4), new Colonne("Passages de carte", Type.ENTIER, 4),
            new Colonne("Retraits", Type.ENTIER, 4), new Colonne("Montant distribué", Type.MONTANT, 6),
            new Colonne("Moyenne par jour", Type.MONTANT, 5), new Colonne("Refus", Type.ENTIER, 4),
            new Colonne("Taux de refus (%)", Type.TEXTE, 4), new Colonne("Motif principal", Type.TEXTE, 8),
            new Colonne("Billets non pris", Type.ENTIER, 4), new Colonne("Cartes capturées", Type.ENTIER, 4),
            new Colonne("Plus longue inactivité (min)", Type.ENTIER, 5), new Colonne("Le", Type.DATE, 4),
            new Colonne("Dernier journal", Type.DATE, 4), new Colonne("Signalements", Type.TEXTE, 14));

    private final ActiviteGab activite;
    private final JournalAudit audit;

    ActiviteGabController(ActiviteGab activite, JournalAudit audit) {
        this.activite = activite;
        this.audit = audit;
    }

    @GetMapping
    Activite activite(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                      @RequestParam(required = false) String terminal) {
        return activite.activite(du, au, terminal);
    }

    @GetMapping("/{terminal}")
    DetailGab detail(@PathVariable String terminal,
                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return activite.detail(terminal, du, au);
    }

    @GetMapping("/export")
    void exporter(@RequestParam String format,
                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                  @RequestParam(required = false) String terminal, HttpServletResponse reponse) throws IOException {
        FormatExport f = FormatExport.depuis(format);
        Activite a = activite.activite(du, au, terminal);
        String titre = "Activité des GAB";
        List<String> criteres = List.of(Criteres.periode(du, au, terminal));
        audit.enregistrer(ActionsAudit.EXPORT, titre + " (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau t = new Tableau(titre, criteres, Securite.login(), COLONNES, ecrire -> a.gabs().forEach(l ->
                ecrire.accept(Arrays.asList(l.terminal(), l.libelle(), l.agence(), l.joursCouverts(), l.sessions(),
                        l.retraits(), l.montant(), l.montantMoyenJour(), l.refuses(), String.valueOf(l.tauxRefus()),
                        l.motifPrincipal(), l.nonPris(), l.cartesCapturees(), l.plusLongueInactivite(), l.dateInactivite(),
                        l.dernierJournal(), l.signalements().stream().map(Signalement::libelle).collect(Collectors.joining(" ; "))))));
        ReponseExport.envoyer(reponse, t, f, "activite_gab");
    }
}
