package com.rapmonet.ingestion;

import java.util.List;

/** BIN des cartes de la banque vus dans les fichiers LIS émission (SW, première présentation). */
public interface BinsEmission {

    /** Six premiers chiffres d'une carte (seuls visibles une fois masquée) et nombre d'opérations. */
    record BinVu(String bin, long operations) {
    }

    /** BIN vus dans les présentations des fichiers SW, du plus fréquent au moins fréquent. */
    List<BinVu> binsVus();
}
