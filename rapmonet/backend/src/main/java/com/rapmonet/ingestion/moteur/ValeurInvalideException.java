package com.rapmonet.ingestion.moteur;

/** La valeur brute d'un attribut ne correspond pas à son type. */
public class ValeurInvalideException extends Exception {

    public ValeurInvalideException(String message) {
        super(message);
    }
}
