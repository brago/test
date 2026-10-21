package com.rapmonet.parametrage;

/** Type de données d'un attribut de fichier positionnel. */
public enum TypeDonnee {
    /** Texte ; les espaces de fin sont retirés. */
    ALPHA,
    /** Nombre entier ou décimal (selon le nombre de décimales implicites). */
    NUMERIQUE,
    /** Date au format JJMMAA. */
    DATE_JJMMAA,
    /** Date au format AAMMJJ. */
    DATE_AAMMJJ,
    /** Date au format AAAAMMJJ. */
    DATE_AAAAMMJJ,
    /** Date julienne au format AAJJJ. */
    DATE_JULIENNE_AAJJJ
}
