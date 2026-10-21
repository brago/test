package com.rapmonet.commun.web;

/** Levée quand une ressource demandée n'existe pas (HTTP 404). */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
