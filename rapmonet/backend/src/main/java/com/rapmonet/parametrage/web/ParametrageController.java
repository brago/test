package com.rapmonet.parametrage.web;

import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.parametrage.FormatFichier;
import com.rapmonet.parametrage.FormatsFichiers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parametrage/formats")
class ParametrageController {

    private final FormatsFichiers formats;

    ParametrageController(FormatsFichiers formats) {
        this.formats = formats;
    }

    @GetMapping
    List<FormatsFichiers.ResumeFormat> lister() {
        return formats.lister();
    }

    @GetMapping("/{code}")
    FormatFichier detail(@PathVariable String code) {
        return formats.trouver(code)
                .orElseThrow(() -> new RessourceIntrouvableException("Format inconnu : " + code));
    }
}
