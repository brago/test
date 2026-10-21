package com.rapmonet.parametrage;

/** Zone d'une ligne : position de début (1 = premier caractère) et longueur. */
public record Zone(int position, int longueur) {

    /** Extrait la zone d'une ligne (complétée par des espaces si elle est trop courte). */
    public String extraire(String ligne) {
        int debut = position - 1;
        int fin = debut + longueur;
        if (ligne.length() >= fin) {
            return ligne.substring(debut, fin);
        }
        String rembourree = ligne + " ".repeat(fin - ligne.length());
        return rembourree.substring(debut, fin);
    }
}
