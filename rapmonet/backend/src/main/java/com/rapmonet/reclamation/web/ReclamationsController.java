package com.rapmonet.reclamation.web;

import com.rapmonet.reclamation.internal.RecherchePorteur;
import com.rapmonet.reclamation.internal.RecherchePorteur.Resultat;
import com.rapmonet.reclamation.internal.ReclamationsService;
import com.rapmonet.reclamation.internal.ReclamationsService.Reclamation;
import com.rapmonet.reclamation.internal.ReclamationsService.Resume;
import com.rapmonet.reclamation.internal.ReclamationsService.Saisie;
import com.rapmonet.securite.Securite;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Réclamations porteurs. La recherche passe par POST : le numéro de carte complet ne doit pas figurer dans une adresse
 * (journaux des serveurs et proxys).
 */
@RestController
@RequestMapping("/api/reclamations")
class ReclamationsController {

    record Action(String action, String commentaire) {
    }

    record Referentiels(Map<String, String> objets, Map<String, String> canaux, Map<String, String> statuts) {
    }

    private final RecherchePorteur recherche;
    private final ReclamationsService reclamations;

    ReclamationsController(RecherchePorteur recherche, ReclamationsService reclamations) {
        this.recherche = recherche;
        this.reclamations = reclamations;
    }

    @PostMapping("/recherche")
    Resultat rechercher(@RequestBody RecherchePorteur.Critere critere) {
        return recherche.rechercher(critere);
    }

    @GetMapping("/referentiels")
    Referentiels referentiels() {
        return new Referentiels(ReclamationsService.OBJETS, ReclamationsService.CANAUX, ReclamationsService.STATUTS);
    }

    @GetMapping
    List<Resume> lister(@RequestParam(required = false) String statut, @RequestParam(required = false) String recherche) {
        return reclamations.lister(statut, recherche);
    }

    @GetMapping("/{id}")
    Reclamation detail(@PathVariable long id) {
        return reclamations.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Reclamation ouvrir(@RequestBody Saisie saisie) {
        return reclamations.ouvrir(saisie, Securite.login());
    }

    @PostMapping("/{id}/actions")
    Reclamation agir(@PathVariable long id, @RequestBody Action a) {
        return reclamations.agir(id, a.action(), a.commentaire(), Securite.login());
    }
}
