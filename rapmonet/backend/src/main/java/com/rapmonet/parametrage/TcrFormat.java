package com.rapmonet.parametrage;

import java.util.List;

/** Structure d'un TCR : attributs ordonnés avec leurs positions. */
public record TcrFormat(String code, String libelle, int rang, List<AttributFormat> attributs) {
}
