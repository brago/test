package com.rapmonet.securite.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Chiffrement AES-GCM des secrets conservés en base (secrets de double authentification). */
@Component
class ChiffrementSecrets {

    private static final SecureRandom ALEA = new SecureRandom();

    private final SecretKeySpec cle;

    ChiffrementSecrets(@Value("${rapmonet.securite.cle-chiffrement}") String cle) {
        try {
            this.cle = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(cle.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    String chiffrer(byte[] clair) {
        try {
            byte[] iv = new byte[12];
            ALEA.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(128, iv));
            byte[] chiffre = c.doFinal(clair);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + chiffre.length).put(iv).put(chiffre).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    byte[] dechiffrer(String texte) {
        try {
            byte[] tout = Base64.getDecoder().decode(texte);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(128, tout, 0, 12));
            return c.doFinal(tout, 12, tout.length - 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret illisible : la clé de chiffrement a-t-elle changé ?", e);
        }
    }
}
