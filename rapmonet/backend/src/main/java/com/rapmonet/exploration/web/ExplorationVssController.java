package com.rapmonet.exploration.web;

import com.rapmonet.commun.export.FormatExport;
import com.rapmonet.commun.export.ReponseExport;
import com.rapmonet.commun.export.Tableau;
import com.rapmonet.commun.export.Tableau.Colonne;
import com.rapmonet.commun.export.Tableau.Type;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.ingestion.ConsultationVss;
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
import java.util.List;

@RestController
@RequestMapping("/api/exploration/vss/rapports")
class ExplorationVssController {

    private final ConsultationVss consultation;
    private final JournalAudit audit;

    ExplorationVssController(ConsultationVss consultation, JournalAudit audit) {
        this.consultation = consultation;
        this.audit = audit;
    }

    @GetMapping
    List<ConsultationVss.ResumeRapport> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String code) {
        return consultation.rechercher(du, au, code);
    }

    @GetMapping("/{id}")
    ConsultationVss.Rapport rapport(@PathVariable long id) {
        return trouver(id);
    }

    /** Export Excel ou PDF d'un rapport, avec la hiérarchie des libellés et toutes ses colonnes. */
    @GetMapping("/{id}/export")
    void exporter(@PathVariable long id, @RequestParam String format, HttpServletResponse reponse) throws IOException {
        FormatExport f = ExplorationLisController.formatExport(format);
        ConsultationVss.Rapport r = trouver(id);
        var resume = r.resume();

        // Devise dans l'en-tête de chaque colonne : compensation pour « CLEARING », règlement sinon.
        // Colonne de devise par ligne seulement si la devise de compensation varie (ex. VSS-900-S).
        List<String> compensations = r.lignes().stream().map(ConsultationVss.Ligne::deviseCompensation)
                .filter(java.util.Objects::nonNull).distinct().toList();
        List<String> reglements = r.lignes().stream().map(ConsultationVss.Ligne::deviseReglement)
                .filter(java.util.Objects::nonNull).distinct().toList();
        boolean compensationVariable = compensations.size() > 1;
        List<Colonne> colonnes = new ArrayList<>();
        colonnes.add(new Colonne("Libellé", Type.TEXTE, 22));
        if (compensationVariable) {
            colonnes.add(new Colonne("Devise compens.", Type.TEXTE, 5));
        }
        for (String c : r.colonnes()) {
            boolean texte = r.lignes().stream().map(l -> l.valeurs().get(c))
                    .allMatch(v -> v == null || v.nombre() == null);
            Type type = texte ? Type.TEXTE : c.contains("COUNT") ? Type.ENTIER : Type.MONTANT;
            List<String> devises = c.contains("CLEARING") ? compensations : reglements;
            String nom = type == Type.MONTANT && devises.size() == 1 ? c + " (" + devises.getFirst() + ")" : c;
            colonnes.add(new Colonne(nom, type, 8));
        }
        String titre = resume.code() + " – " + (resume.libelleType() != null ? resume.libelleType() : resume.titre());
        List<String> criteres = List.of(
                "Fichier : " + resume.nomFichier() + " · Date de traitement : "
                        + DateTimeFormatter.ofPattern("dd/MM/yyyy").format(resume.dateTraitement()),
                "SRE : " + resume.sreId() + " " + resume.sreNom()
                        + (r.rollup() != null ? " · Rollup : " + r.rollup() : "")
                        + (r.entiteFonds() != null ? " · Entité de règlement : " + r.entiteFonds() : ""),
                resume.titre() + (resume.sansDonnees() ? " – aucune donnée" : ""));
        Tableau tableau = new Tableau(titre, criteres, Securite.login(), colonnes, ecrire -> r.lignes().forEach(l -> {
            List<Object> valeurs = new ArrayList<>();
            valeurs.add("   ".repeat(l.niveau()) + l.libelle());
            if (compensationVariable) {
                valeurs.add(l.deviseCompensation());
            }
            for (String c : r.colonnes()) {
                ConsultationVss.Valeur v = l.valeurs().get(c);
                valeurs.add(v == null ? null : v.nombre() != null ? new Tableau.MontantSens(v.nombre(), v.sens()) : v.texte());
            }
            ecrire.accept(valeurs);
        }));
        audit.enregistrer(ActionsAudit.EXPORT, titre + " (" + f.extension() + ")", resume.nomFichier());
        ReponseExport.envoyer(reponse, tableau, f, resume.code().toLowerCase().replace('-', '_') + "_" + resume.dateTraitement());
    }

    private ConsultationVss.Rapport trouver(long id) {
        return consultation.rapport(id).orElseThrow(() -> new RessourceIntrouvableException("Rapport VSS introuvable : " + id));
    }
}
