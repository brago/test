package com.rapmonet.securite.web;

import com.rapmonet.securite.internal.PolitiqueSecurite;
import com.rapmonet.securite.internal.PolitiqueSecurite.Politique;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/securite/politique")
class PolitiqueController {

    private final PolitiqueSecurite politique;

    PolitiqueController(PolitiqueSecurite politique) {
        this.politique = politique;
    }

    @GetMapping
    Politique lire() {
        return politique.lire();
    }

    @PutMapping
    Politique modifier(@RequestBody Politique p) {
        return politique.modifier(p);
    }
}
