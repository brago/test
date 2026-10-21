package com.rapmonet.systeme;

/**
 * Informations générales renvoyées par {@code GET /api/systeme/info}.
 */
public record InfoSysteme(String application, String version, BaseDeDonnees baseDeDonnees) {

    public record BaseDeDonnees(String produit, String version, String schemaVersion) {
    }
}
