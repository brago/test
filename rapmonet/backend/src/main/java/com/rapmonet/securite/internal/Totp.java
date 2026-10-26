package com.rapmonet.securite.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/** Codes à usage unique basés sur le temps (RFC 6238 : HMAC-SHA1, 6 chiffres, pas de 30 secondes). */
final class Totp {

    static final int PERIODE_SECONDES = 30;
    static final int CHIFFRES = 6;
    /** Décalage d'horloge toléré, en pas, de part et d'autre du pas courant. */
    static final int FENETRE = 1;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEA = new SecureRandom();

    private Totp() {
    }

    static byte[] nouveauSecret() {
        byte[] secret = new byte[20];
        ALEA.nextBytes(secret);
        return secret;
    }

    static long pas(Instant instant) {
        return instant.getEpochSecond() / PERIODE_SECONDES;
    }

    static String code(byte[] secret, long pas) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(pas).array());
            int decalage = h[h.length - 1] & 0x0f;
            int binaire = ((h[decalage] & 0x7f) << 24) | ((h[decalage + 1] & 0xff) << 16)
                    | ((h[decalage + 2] & 0xff) << 8) | (h[decalage + 3] & 0xff);
            return String.format("%06d", binaire % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Vérifie un code saisi. Un code déjà utilisé (pas inférieur ou égal au dernier accepté) est refusé.
     *
     * @return le pas reconnu, ou -1 si le code est refusé
     */
    static long verifier(byte[] secret, String saisi, Long dernierPas, Instant maintenant) {
        String code = saisi == null ? "" : saisi.replaceAll("\\s", "");
        if (!code.matches("\\d{" + CHIFFRES + "}")) {
            return -1;
        }
        long courant = pas(maintenant);
        for (long p = courant - FENETRE; p <= courant + FENETRE; p++) {
            if ((dernierPas == null || p > dernierPas) && egal(code(secret, p), code)) {
                return p;
            }
        }
        return -1;
    }

    private static boolean egal(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    static String base32(byte[] donnees) {
        StringBuilder sb = new StringBuilder();
        int tampon = 0;
        int bits = 0;
        for (byte b : donnees) {
            tampon = (tampon << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((tampon >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET.charAt((tampon << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] depuisBase32(String texte) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        int tampon = 0;
        int bits = 0;
        for (char c : texte.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT).toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Caractère base32 invalide : " + c);
            }
            tampon = (tampon << 5) | v;
            bits += 5;
            if (bits >= 8) {
                sortie.write((tampon >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return sortie.toByteArray();
    }
}
