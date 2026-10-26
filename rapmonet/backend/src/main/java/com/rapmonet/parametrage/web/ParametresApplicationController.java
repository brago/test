package com.rapmonet.parametrage.web;

import com.rapmonet.parametrage.internal.ParametresApplication;
import com.rapmonet.parametrage.internal.ParametresApplication.Modification;
import com.rapmonet.parametrage.internal.ParametresApplication.Parametre;
import com.rapmonet.securite.Securite;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Paramètres de l'application (menu PARAMETRAGE_APPLICATION). */
@RestController
@RequestMapping("/api/parametrage/application")
class ParametresApplicationController {

    record DemandeModification(String valeur, String motif) {
    }

    private final ParametresApplication parametres;

    ParametresApplicationController(ParametresApplication parametres) {
        this.parametres = parametres;
    }

    @GetMapping
    List<Parametre> lister() {
        return parametres.lister();
    }

    @GetMapping("/historique")
    List<Modification> historique() {
        return parametres.historique();
    }

    @PutMapping("/{cle}")
    Parametre modifier(@PathVariable String cle, @RequestBody DemandeModification d) {
        return parametres.modifier(cle, d.valeur(), d.motif(), Securite.login());
    }
}
