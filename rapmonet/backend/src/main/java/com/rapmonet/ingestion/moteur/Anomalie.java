package com.rapmonet.ingestion.moteur;

/**
 * Anomalie détectée à la lecture.
 *
 * @param ligne numéro de ligne dans le fichier (null si l'anomalie concerne le fichier entier)
 */
public record Anomalie(Integer ligne, Gravite gravite, String message) {

    public static Anomalie bloquante(Integer ligne, String message) {
        return new Anomalie(ligne, Gravite.BLOQUANTE, message);
    }

    public static Anomalie avertissement(Integer ligne, String message) {
        return new Anomalie(ligne, Gravite.AVERTISSEMENT, message);
    }
}
