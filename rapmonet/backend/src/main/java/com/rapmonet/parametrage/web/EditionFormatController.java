package com.rapmonet.parametrage.web;

import com.rapmonet.parametrage.internal.EditionFormats;
import com.rapmonet.parametrage.internal.EditionFormats.Champ;
import com.rapmonet.parametrage.internal.EditionFormats.Modification;
import com.rapmonet.parametrage.internal.EditionFormats.SaisieChamp;
import com.rapmonet.parametrage.internal.EditionFormats.SaisieTc;
import com.rapmonet.securite.Securite;
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

/** Modification du format d'un fichier positionnel : consultation libre, modification réservée au menu PARAMETRAGE_LIS. */
@RestController
@RequestMapping("/api/parametrage/formats/{format}")
class EditionFormatController {

    record DemandeChamp(SaisieChamp champ, String motif) {
    }

    record DemandeStructure(List<String> champs, String motif) {
    }

    record DemandeTc(String libelle, String description, String motif) {
    }

    record DemandeNouveauTc(SaisieTc tc, String motif) {
    }

    private final EditionFormats edition;

    EditionFormatController(EditionFormats edition) {
        this.edition = edition;
    }

    @GetMapping("/champs")
    List<Champ> champs(@PathVariable String format) {
        return edition.champs(format);
    }

    @GetMapping("/historique")
    List<Modification> historique(@PathVariable String format) {
        return edition.historique(format);
    }

    @PutMapping("/champs/{code}")
    Champ modifierChamp(@PathVariable String format, @PathVariable String code, @RequestBody DemandeChamp d) {
        return edition.modifierChamp(format, code, d.champ(), d.motif(), Securite.login());
    }

    @PostMapping("/champs")
    @ResponseStatus(HttpStatus.CREATED)
    Champ creerChamp(@PathVariable String format, @RequestBody DemandeChamp d) {
        return edition.creerChamp(format, d.champ(), d.motif(), Securite.login());
    }

    @PutMapping("/tcs/{tc}/tcrs/{tcr}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void modifierStructure(@PathVariable String format, @PathVariable String tc, @PathVariable String tcr,
                           @RequestBody DemandeStructure d) {
        edition.modifierStructure(format, tc, tcr, d.champs(), d.motif(), Securite.login());
    }

    @PutMapping("/tcs/{tc}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void modifierTc(@PathVariable String format, @PathVariable String tc, @RequestBody DemandeTc d) {
        edition.modifierTc(format, tc, d.libelle(), d.description(), d.motif(), Securite.login());
    }

    @PostMapping("/tcs")
    @ResponseStatus(HttpStatus.CREATED)
    void creerTc(@PathVariable String format, @RequestBody DemandeNouveauTc d) {
        edition.creerTc(format, d.tc(), d.motif(), Securite.login());
    }
}
