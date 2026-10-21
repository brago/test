package com.rapmonet.parametrage;

import java.util.List;
import java.util.Optional;

/** Accès en lecture aux formats de fichiers paramétrés. */
public interface FormatsFichiers {

    Optional<FormatFichier> trouver(String code);

    List<ResumeFormat> lister();

    record ResumeFormat(String code, String famille, String libelle) {
    }
}
