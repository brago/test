package com.rapmonet.securite.web;

import com.rapmonet.securite.internal.UtilisateursService;
import com.rapmonet.securite.internal.UtilisateursService.Utilisateur;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/securite/utilisateurs")
class UtilisateursController {

    record Reinitialisation(String nouveau, boolean doitChanger) {
    }

    private final UtilisateursService service;

    UtilisateursController(UtilisateursService service) {
        this.service = service;
    }

    @GetMapping
    List<Utilisateur> lister() {
        return service.lister();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Utilisateur creer(@RequestBody UtilisateursService.Creation creation) {
        return service.creer(creation);
    }

    @PutMapping("/{login}")
    Utilisateur modifier(@PathVariable String login, @RequestBody UtilisateursService.Modification modification) {
        return service.modifier(login, modification);
    }

    @PostMapping("/{login}/deverrouiller")
    Utilisateur deverrouiller(@PathVariable String login) {
        return service.deverrouiller(login);
    }

    @PostMapping("/{login}/mot-de-passe")
    Utilisateur reinitialiser(@PathVariable String login, @RequestBody Reinitialisation r) {
        return service.reinitialiserMotDePasse(login, r.nouveau(), r.doitChanger());
    }
}
