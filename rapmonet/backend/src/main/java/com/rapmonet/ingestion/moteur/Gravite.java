package com.rapmonet.ingestion.moteur;

/** Gravité d'une anomalie détectée à la lecture d'un fichier. */
public enum Gravite {
    /** Le fichier est rejeté. */
    BLOQUANTE,
    /** Le fichier est intégré, l'anomalie est conservée pour analyse. */
    AVERTISSEMENT
}
