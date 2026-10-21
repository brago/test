package com.rapmonet.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabrique un fichier LIS synthétique conforme à la spécification LIS 3.038
 * (aucune donnée réelle) : en-tête, cinq sous-fichiers, retraits TC 07 dans le FL, fin.
 */
final class FichierLisSynthetique {

    private final List<StringBuilder> lignes = new ArrayList<>();

    private StringBuilder nouvelle(String tc, String tcr) {
        StringBuilder l = new StringBuilder(" ".repeat(256));
        l.replace(0, 2, tc);
        l.replace(2, 8, "%06d".formatted(lignes.size() + 1));
        l.replace(8, 9, tcr);
        lignes.add(l);
        return l;
    }

    private static void ecrire(StringBuilder l, int position, String valeur) {
        l.replace(position - 1, position - 1 + valeur.length(), valeur);
    }

    /**
     * @param jjmmaa  date de journée
     * @param retraits couples {numéro de carte, montant}
     */
    static byte[] creer(String jjmmaa, String numeroFichier, String[][] retraits) {
        FichierLisSynthetique f = new FichierLisSynthetique();
        StringBuilder entete = f.nouvelle("90", "0");
        ecrire(entete, 10, "000343");
        ecrire(entete, 16, jjmmaa);
        ecrire(entete, 22, numeroFichier);
        ecrire(entete, 26, "3.038");
        ecrire(entete, 41, "101010");
        for (String[] sf : new String[][]{{"92", "93"}, {"94", "95"}, {"96", "97"}, {"98", "99"}, {"80", "81"}}) {
            int debut = f.lignes.size();
            f.nouvelle(sf[0], "0");
            if (sf[0].equals("98")) {
                for (String[] r : retraits) {
                    StringBuilder tcr0 = f.nouvelle("07", "0");
                    ecrire(tcr0, 26, "GAB SYNTHETIQUE");
                    ecrire(tcr0, 67, "6011");
                    ecrire(tcr0, 75, "12345678");
                    ecrire(tcr0, 148, "3");
                    ecrire(tcr0, 149, r[0]);
                    StringBuilder tcr1 = f.nouvelle("07", "1");
                    ecrire(tcr1, 10, jjmmaa);
                    ecrire(tcr1, 16, "123456");
                    ecrire(tcr1, 24, "74321573041003010279220");
                    ecrire(tcr1, 56, "0");
                    ecrire(tcr1, 57, "%012d".formatted(Long.parseLong(r[1])));
                    ecrire(tcr1, 164, "952");
                }
            }
            StringBuilder fin = f.nouvelle(sf[1], "0");
            ecrire(fin, 10, "%06d".formatted(f.lignes.size() - debut));
        }
        StringBuilder fin = f.nouvelle("91", "0");
        ecrire(fin, 10, "%06d".formatted(f.lignes.size()));
        StringBuilder texte = new StringBuilder();
        f.lignes.forEach(l -> texte.append(l).append("\r\n"));
        return texte.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
