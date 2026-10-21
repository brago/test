package com.rapmonet.securite.web;

import com.rapmonet.securite.internal.ProfilsService;
import com.rapmonet.securite.internal.ProfilsService.Profil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/securite")
class ProfilsController {

    private final ProfilsService service;

    ProfilsController(ProfilsService service) {
        this.service = service;
    }

    @GetMapping("/menus")
    List<ProfilsService.Menu> menus() {
        return service.menus();
    }

    @GetMapping("/profils")
    List<Profil> lister() {
        return service.lister();
    }

    @PostMapping("/profils")
    @ResponseStatus(HttpStatus.CREATED)
    Profil creer(@RequestBody ProfilsService.Saisie saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/profils/{code}")
    Profil modifier(@PathVariable String code, @RequestBody ProfilsService.Saisie saisie) {
        return service.modifier(code, saisie);
    }

    @DeleteMapping("/profils/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void supprimer(@PathVariable String code) {
        service.supprimer(code);
    }
}
