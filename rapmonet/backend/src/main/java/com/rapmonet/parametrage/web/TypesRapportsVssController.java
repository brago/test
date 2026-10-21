package com.rapmonet.parametrage.web;

import com.rapmonet.parametrage.TypesRapportsVss;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parametrage/vss/types-rapports")
class TypesRapportsVssController {

    record Integration(boolean integre) {
    }

    private final TypesRapportsVss types;

    TypesRapportsVssController(TypesRapportsVss types) {
        this.types = types;
    }

    @GetMapping
    List<TypesRapportsVss.TypeRapportVss> lister() {
        return types.lister();
    }

    @PutMapping("/{code}")
    TypesRapportsVss.TypeRapportVss modifier(@PathVariable String code, @RequestBody Integration integration) {
        return types.modifierIntegration(code, integration.integre());
    }
}
