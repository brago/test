package com.rapmonet.parametrage;

import java.util.List;

/** Sous-fichier délimité par un TC d'en-tête et un TC de fin. */
public record SousFichierFormat(String code, String libelle, String description,
                                String tcEntete, String tcFin, List<String> tcAutorises) {
}
