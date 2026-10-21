package com.rapmonet.gab;

/** BIN des cartes émises par la banque (paramétrage « BIN cartes »). */
public interface BinsBanque {

    /** Vrai si le numéro de carte (en clair ou masqué) commence par un BIN de la banque. */
    boolean carteDeLaBanque(String carte);

    /** Vrai si au moins un BIN est paramétré. */
    boolean binsParametres();
}
