package com.rapmonet.gab.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

final class Pdf {

    private Pdf() {
    }

    static ResponseEntity<byte[]> reponse(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nom, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(contenu);
    }
}
