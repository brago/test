package com.rapmonet.ingestion.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.function.UnaryOperator;

/**
 * Empreinte HMAC-SHA256 d'un numéro de carte : permet de retrouver et rapprocher les
 * transactions d'une même carte sans conserver le numéro en clair (PCI-DSS).
 * La clé doit être secrète et identique sur tous les environnements qui partagent les données.
 */
@Component
class EmpreinteCarte implements UnaryOperator<String> {

    private final SecretKeySpec cle;

    EmpreinteCarte(@Value("${rapmonet.securite.cle-empreinte-carte}") String cle) {
        if (cle == null || cle.length() < 16) {
            throw new IllegalStateException("rapmonet.securite.cle-empreinte-carte doit contenir au moins 16 caractères");
        }
        this.cle = new SecretKeySpec(cle.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public String apply(String numeroCarte) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cle);
            return HexFormat.of().formatHex(mac.doFinal(numeroCarte.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
