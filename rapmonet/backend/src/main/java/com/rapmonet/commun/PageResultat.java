package com.rapmonet.commun;

import java.util.List;

/** Une page de résultats de recherche. */
public record PageResultat<T>(List<T> elements, long total, int page, int taille) {
}
