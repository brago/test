package com.rapmonet.gab;

import java.util.Map;

/** Libellés du paramétrage GAB, pour les indicateurs et les états. */
public interface ReferentielGab {

    /** Libellé de chaque GAB paramétré, par terminal. */
    Map<String, String> libellesGab();

    /** Libellé produit de chaque BIN de la banque, par BIN. */
    Map<String, String> produitsBin();
}
