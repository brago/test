package com.rapmonet.commun.web;

/** Levée quand une règle métier est violée (HTTP 422). */
public class RegleMetierException extends RuntimeException {

    public RegleMetierException(String message) {
        super(message);
    }
}
