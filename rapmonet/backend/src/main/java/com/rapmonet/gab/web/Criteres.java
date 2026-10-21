package com.rapmonet.gab.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Libellés des critères et statuts dans les exports. */
final class Criteres {

    private static final DateTimeFormatter JJ = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Criteres() {
    }

    static String periode(LocalDate du, LocalDate au, String terminal) {
        return "Période : " + (du == null ? "début" : JJ.format(du)) + " au " + (au == null ? "aujourd'hui" : JJ.format(au))
                + " · GAB : " + (terminal == null || terminal.isBlank() ? "tous" : terminal);
    }

    static String statut(String statut) {
        if (statut == null || statut.isBlank()) {
            return "tous";
        }
        return switch (statut) {
            case "SAISI" -> "À valider";
            case "VALIDE" -> "Validé";
            case "ANNULE" -> "Annulé";
            default -> statut;
        };
    }
}
