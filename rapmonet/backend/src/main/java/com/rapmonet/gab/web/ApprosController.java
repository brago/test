package com.rapmonet.gab.web;

import com.rapmonet.commun.PageResultat;
import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.gab.internal.ApprosService;
import com.rapmonet.gab.internal.ApprosService.Appro;
import com.rapmonet.gab.internal.ApprosService.Critere;
import com.rapmonet.gab.internal.ApprosService.Saisie;
import com.rapmonet.gab.internal.JournalGab;
import com.rapmonet.gab.internal.ParametrageGabService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gab/appros")
class ApprosController {

    private static final List<Colonne> COLONNES = List.of(
            new Colonne("Référence", Type.TEXTE, 4),
            new Colonne("Date appro", Type.DATE_HEURE, 6),
            new Colonne("GAB", Type.TEXTE, 4),
            new Colonne("Nom du GAB", Type.TEXTE, 8),
            new Colonne("Agence", Type.TEXTE, 3),
            new Colonne("Compte caisse", Type.TEXTE, 6),
            new Colonne("Gabiste", Type.TEXTE, 7),
            new Colonne("Cassette A", Type.ENTIER, 3),
            new Colonne("Cassette B", Type.ENTIER, 3),
            new Colonne("Cassette C", Type.ENTIER, 3),
            new Colonne("Cassette D", Type.ENTIER, 3),
            new Colonne("Montant", Type.MONTANT, 6),
            new Colonne("Statut", Type.TEXTE, 4),
            new Colonne("Saisi par", Type.TEXTE, 4),
            new Colonne("Validé / annulé par", Type.TEXTE, 4),
            new Colonne("Arrêté", Type.TEXTE, 4));

    private final ApprosService service;
    private final ParametrageGabService parametrage;
    private final JournalGab journal;
    private final JournalAudit audit;

    ApprosController(ApprosService service, ParametrageGabService parametrage, JournalGab journal, JournalAudit audit) {
        this.service = service;
        this.parametrage = parametrage;
        this.journal = journal;
        this.audit = audit;
    }

    @GetMapping
    PageResultat<Appro> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String terminal, @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int taille) {
        return service.rechercher(new Critere(du, au, terminal, statut), page, Math.min(taille, 500));
    }

    @GetMapping("/{id}")
    Appro appro(@PathVariable long id) {
        return service.appro(id);
    }

    /** Cassettes du GAB et passages du gabiste lus dans le journal ce jour-là (pour pré-remplir ou contrôler la saisie). */
    @GetMapping("/preparation")
    Map<String, Object> preparation(@RequestParam String terminal,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ParametrageGabService.Gab gab = parametrage.gab(terminal);
        var cassettes = parametrage.profil(gab.profilCode()).cassettes();
        return Map.of("gab", gab, "cassettes", cassettes,
                "passages", journal.passages(terminal, date).stream().filter(p -> p.ajoutes() != null).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Appro creer(@RequestBody Saisie saisie) {
        return service.creer(saisie, Securite.login());
    }

    @PutMapping("/{id}")
    Appro modifier(@PathVariable long id, @RequestBody Saisie saisie) {
        return service.modifier(id, saisie, Securite.login());
    }

    @PostMapping("/{id}/validation")
    Appro valider(@PathVariable long id) {
        return service.valider(id, Securite.login());
    }

    @PostMapping("/{id}/annulation")
    Appro annuler(@PathVariable long id, @RequestBody Map<String, String> corps) {
        return service.annuler(id, corps.get("motif"), Securite.login());
    }

    @GetMapping("/{id}/rapport")
    ResponseEntity<byte[]> rapport(@PathVariable long id) {
        Appro a = service.appro(id);
        audit.enregistrer(ActionsAudit.EXPORT, "Rapport appro " + a.reference(), "PDF");
        return Pdf.reponse(RapportsGab.appro(a, Securite.login()), "appro_" + a.terminal() + "_" + a.reference() + ".pdf");
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
        audit.enregistrer(ActionsAudit.EXPORT, "Historique des appros GAB (" + f.extension() + ")", String.join(" ; ", criteres));
        Tableau t = new Tableau("Historique des approvisionnements GAB", criteres, Securite.login(), COLONNES,
                ecrire -> service.parcourir(c, a -> ecrire.accept(Arrays.asList(a.reference(), a.dateAppro(), a.terminal(),
                        a.libelleGab(), a.agence(), a.compteCaisse(), a.gabiste(), nombre(a, "A"), nombre(a, "B"),
                        nombre(a, "C"), nombre(a, "D"), a.montant(), Criteres.statut(a.statut()), a.saisiPar(),
                        a.decidePar(), a.arrete()))));
        ReponseExport.envoyer(reponse, t, f, "appros_gab");
    }

    private static Integer nombre(Appro a, String cassette) {
        return a.billetage().stream().filter(l -> l.cassette().equals(cassette)).map(ApprosService.Ligne::nombre)
                .findFirst().orElse(null);
    }
}
