package com.rapmonet.parametrage;

import java.util.List;
import java.util.Optional;

/** Types de rapports VSS connus et choix de ceux à intégrer. */
public interface TypesRapportsVss {

    record TypeRapportVss(String code, String libelle, String description, boolean integre) {
    }

    List<TypeRapportVss> lister();

    Optional<TypeRapportVss> trouver(String code);

    TypeRapportVss modifierIntegration(String code, boolean integre);
}
