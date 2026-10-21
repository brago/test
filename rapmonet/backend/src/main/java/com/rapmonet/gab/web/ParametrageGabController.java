package com.rapmonet.gab.web;

import com.rapmonet.gab.internal.ParametrageGabService;
import com.rapmonet.gab.internal.ParametrageGabService.Bin;
import com.rapmonet.gab.internal.ParametrageGabService.Gab;
import com.rapmonet.gab.internal.ParametrageGabService.Profil;
import com.rapmonet.securite.Securite;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Paramétrage des GAB, des profils GAB et des BIN cartes. Lecture : tout utilisateur ; écriture : menu PARAMETRAGE_GAB. */
@RestController
@RequestMapping("/api/parametrage/gab")
class ParametrageGabController {

    private final ParametrageGabService service;

    ParametrageGabController(ParametrageGabService service) {
        this.service = service;
    }

    @GetMapping("/terminaux")
    List<Gab> gabs() {
        return service.gabs();
    }

    @PutMapping("/terminaux/{terminal}")
    Gab enregistrerGab(@PathVariable String terminal, @RequestBody Gab gab) {
        return service.enregistrerGab(new Gab(terminal, gab.libelle(), gab.localisation(), gab.agence(), gab.marque(),
                gab.profilCode(), gab.compteCaisse(), gab.compteManquant(), gab.compteExcedent(), gab.actif()), Securite.login());
    }

    @DeleteMapping("/terminaux/{terminal}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimerGab(@PathVariable String terminal) {
        service.supprimerGab(terminal, Securite.login());
    }

    @GetMapping("/profils")
    List<Profil> profils() {
        return service.profils();
    }

    @PutMapping("/profils/{code}")
    Profil enregistrerProfil(@PathVariable String code, @RequestBody Profil profil) {
        return service.enregistrerProfil(new Profil(code, profil.libelle(), profil.cassettes()), Securite.login());
    }

    @DeleteMapping("/profils/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimerProfil(@PathVariable String code) {
        service.supprimerProfil(code, Securite.login());
    }

    @GetMapping("/bins")
    List<Bin> bins() {
        return service.bins();
    }

    @PutMapping("/bins/{bin}")
    Bin enregistrerBin(@PathVariable String bin, @RequestBody Bin b) {
        return service.enregistrerBin(new Bin(bin, b.libelle(), b.typeCarte()), Securite.login());
    }

    @DeleteMapping("/bins/{bin}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimerBin(@PathVariable String bin) {
        service.supprimerBin(bin, Securite.login());
    }
}
