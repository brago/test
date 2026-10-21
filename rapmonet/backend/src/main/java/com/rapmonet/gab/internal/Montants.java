package com.rapmonet.gab.internal;

import java.text.NumberFormat;
import java.util.Locale;

/** Montants en XOF avec séparateur de milliers (espace simple, lisible dans les PDF). */
final class Montants {

    private Montants() {
    }

    static String texte(Long v) {
        return v == null ? "" : NumberFormat.getIntegerInstance(Locale.FRANCE).format(v).replace(' ', ' ').replace(' ', ' ');
    }
}
