package com.rapmonet.ingestion.moteur;

import com.rapmonet.parametrage.AttributFormat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;

/** Convertit la valeur brute d'un attribut selon son type. */
public final class DecodeurAttribut {

    private DecodeurAttribut() {
    }

    /**
     * @return la valeur décodée : {@link String}, {@link Long}, {@link BigDecimal}, {@link LocalDate},
     *         ou null si la zone est vide (ou une date à zéro)
     */
    public static Object decoder(AttributFormat attribut, String brut) throws ValeurInvalideException {
        String valeur = brut.strip();
        return switch (attribut.type()) {
            case ALPHA -> brut.stripTrailing();
            case NUMERIQUE -> nombre(valeur, attribut.decimales());
            case DATE_JJMMAA -> date(valeur, 6, () -> LocalDate.of(
                    2000 + entier(valeur, 4, 6), entier(valeur, 2, 4), entier(valeur, 0, 2)));
            case DATE_AAMMJJ -> date(valeur, 6, () -> LocalDate.of(
                    2000 + entier(valeur, 0, 2), entier(valeur, 2, 4), entier(valeur, 4, 6)));
            case DATE_AAAAMMJJ -> date(valeur, 8, () -> LocalDate.of(
                    entier(valeur, 0, 4), entier(valeur, 4, 6), entier(valeur, 6, 8)));
            case DATE_JULIENNE_AAJJJ -> date(valeur, 5, () -> LocalDate.ofYearDay(
                    2000 + entier(valeur, 0, 2), entier(valeur, 2, 5)));
        };
    }

    private static Object nombre(String valeur, int decimales) throws ValeurInvalideException {
        if (valeur.isEmpty()) {
            return null;
        }
        if (!valeur.chars().allMatch(Character::isDigit)) {
            throw new ValeurInvalideException("nombre attendu, reçu « " + valeur + " »");
        }
        if (decimales > 0) {
            return new BigDecimal(new BigInteger(valeur), decimales);
        }
        return valeur.length() <= 18 ? (Object) Long.parseLong(valeur) : new BigDecimal(valeur);
    }

    private interface Fabrique {
        LocalDate creer();
    }

    private static LocalDate date(String valeur, int longueur, Fabrique fabrique) throws ValeurInvalideException {
        if (valeur.isEmpty() || valeur.chars().allMatch(c -> c == '0')) {
            return null;
        }
        if (valeur.length() != longueur || !valeur.chars().allMatch(Character::isDigit)) {
            throw new ValeurInvalideException("date attendue, reçu « " + valeur + " »");
        }
        try {
            return fabrique.creer();
        } catch (DateTimeException e) {
            throw new ValeurInvalideException("date invalide « " + valeur + " »");
        }
    }

    private static int entier(String valeur, int debut, int fin) {
        return Integer.parseInt(valeur.substring(debut, fin));
    }
}
