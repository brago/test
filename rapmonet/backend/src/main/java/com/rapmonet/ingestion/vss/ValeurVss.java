package com.rapmonet.ingestion.vss;

import java.math.BigDecimal;

/**
 * Valeur d'une cellule de rapport VSS : un nombre (avec son sens DB / CR éventuel)
 * ou un texte (ex. identifiant de table de taux « A1359 »).
 */
public record ValeurVss(BigDecimal nombre, String sens, String texte) {

    /** Montant signé : négatif pour un débit (DB). */
    public BigDecimal signe() {
        return nombre == null ? null : "DB".equals(sens) ? nombre.negate() : nombre;
    }
}
