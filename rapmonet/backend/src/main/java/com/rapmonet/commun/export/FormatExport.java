package com.rapmonet.commun.export;

import java.util.Locale;

/** Formats d'export proposés à l'utilisateur. */
public enum FormatExport {
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    private final String typeMime;
    private final String extension;

    FormatExport(String typeMime, String extension) {
        this.typeMime = typeMime;
        this.extension = extension;
    }

    public String typeMime() {
        return typeMime;
    }

    public String extension() {
        return extension;
    }

    /** Accepte « xlsx », « excel » ou « pdf » (casse indifférente). */
    public static FormatExport depuis(String valeur) {
        return switch (valeur == null ? "" : valeur.toLowerCase(Locale.ROOT)) {
            case "pdf" -> PDF;
            case "xlsx", "excel" -> XLSX;
            default -> throw new IllegalArgumentException("Format d'export inconnu : " + valeur);
        };
    }
}
