package com.rapmonet.alertes.web;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.internal.AlertesService;
import com.rapmonet.securite.Habilitations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Alertes de l'utilisateur connecté, filtrées sur ses menus. */
@RestController
@RequestMapping("/api/alertes")
class AlertesController {

    private final AlertesService service;

    AlertesController(AlertesService service) {
        this.service = service;
    }

    @GetMapping
    List<Alerte> alertes(Authentication authentification) {
        Set<String> menus = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(Habilitations.PREFIXE_MENU)).map(a -> a.substring(Habilitations.PREFIXE_MENU.length())).collect(Collectors.toSet());
        return service.alertes(menus);
    }
}
