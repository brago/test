package com.rapmonet.commun.export;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Envoie un export en téléchargement dans la réponse HTTP. */
public final class ReponseExport {

    private ReponseExport() {
    }

    /** @param prefixe début du nom de fichier, complété par la date et l'extension */
    public static void envoyer(HttpServletResponse reponse, Tableau tableau, FormatExport format, String prefixe)
            throws IOException {
        String nom = prefixe + "_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                + "." + format.extension();
        reponse.setContentType(format.typeMime());
        reponse.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString());
        ExportTableau.ecrire(tableau, format, reponse.getOutputStream());
        reponse.flushBuffer();
    }
}
