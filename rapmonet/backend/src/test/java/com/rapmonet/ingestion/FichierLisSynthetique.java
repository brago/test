package com.rapmonet.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabrique un fichier LIS synthétique conforme à la spécification LIS 3.038
 * (aucune donnée réelle) : en-tête, cinq sous-fichiers, retraits TC 07 dans le FL, fin.
 */
public final class FichierLisSynthetique {

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
     * Opération de la famille présentation / chargeback / redressement (même structure de TCR 0 et 1).
     *
     * @param tc              05, 07, 15, 17, 25, 37…
     * @param usage           1 = première présentation ou premier chargeback, 2 = représentation ou second chargeback
     * @param dateTransaction JJMMAA
     */
    public record Operation(String tc, String usage, String carte, long montant, String arn, String rrn, String terminal,
                            String dateTransaction, String motif, String message, String fid, String rid) {
    }

    /** Avis d'acquisition (TC 49) : transaction d'une carte d'une autre banque sur un GAB ou TPE de la banque. */
    public record AvisAcquisition(String carte, long montant, String terminal, String mcc, String dateTransaction,
                                  String marchand, String produit, long interchangeCentimes) {

        /** Carte GIM d'une autre banque (indicateur de produit 3, interchange de 2,50). */
        public AvisAcquisition(String carte, long montant, String terminal, String mcc, String dateTransaction, String marchand) {
            this(carte, montant, terminal, mcc, dateTransaction, marchand, "3", 250);
        }
    }

    /** Fichier dont le sous-fichier GIM local (FL) contient les opérations données. */
    public static byte[] creer(String jjmmaa, String numeroFichier, List<Operation> operations) {
        return creer(jjmmaa, numeroFichier, operations, List.of());
    }

    /** Fichier dont le sous-fichier GIM local (FL) contient les opérations puis les avis d'acquisition. */
    public static byte[] creer(String jjmmaa, String numeroFichier, List<Operation> operations,
                               List<AvisAcquisition> avis) {
        FichierLisSynthetique f = new FichierLisSynthetique();
        f.entete(jjmmaa, numeroFichier);
        for (String[] sf : SOUS_FICHIERS) {
            int debut = f.lignes.size();
            f.nouvelle(sf[0], "0");
            if (sf[0].equals("98")) {
                for (Operation o : operations) {
                    StringBuilder tcr0 = f.nouvelle(o.tc(), "0");
                    ecrire(tcr0, 26, "GAB SYNTHETIQUE");
                    ecrire(tcr0, 67, "6011");
                    ecrire(tcr0, 75, o.terminal());
                    ecrire(tcr0, 83, o.usage());
                    if (o.message() != null) {
                        ecrire(tcr0, 87, o.message());
                    }
                    if (o.motif() != null) {
                        ecrire(tcr0, 137, o.motif());
                        ecrire(tcr0, 141, "000001");
                    }
                    ecrire(tcr0, 148, "3");
                    ecrire(tcr0, 149, o.carte());
                    StringBuilder tcr1 = f.nouvelle(o.tc(), "1");
                    ecrire(tcr1, 10, o.dateTransaction());
                    ecrire(tcr1, 16, "123456");
                    ecrire(tcr1, 24, o.arn());
                    ecrire(tcr1, 47, o.fid());
                    ecrire(tcr1, 56, "0");
                    ecrire(tcr1, 57, "%012d".formatted(o.montant()));
                    ecrire(tcr1, 69, o.rid());
                    ecrire(tcr1, 79, "%012d".formatted(o.montant()));
                    ecrire(tcr1, 146, o.rrn());
                    ecrire(tcr1, 158, "120000");
                    ecrire(tcr1, 164, "952");
                    ecrire(tcr1, 167, "952");
                }
                for (AvisAcquisition a : avis) {
                    StringBuilder tcr0 = f.nouvelle("49", "0");
                    ecrire(tcr0, 10, "18");
                    ecrire(tcr0, 27, a.terminal());
                    ecrire(tcr0, 42, a.marchand());
                    ecrire(tcr0, 83, a.mcc());
                    ecrire(tcr0, 98, "0");
                    ecrire(tcr0, 99, a.produit());
                    ecrire(tcr0, 100, a.carte());
                    ecrire(tcr0, 125, a.dateTransaction());
                    ecrire(tcr0, 131, "123456");
                    ecrire(tcr0, 151, "000000000001");
                    ecrire(tcr0, 139, "%012d".formatted(a.montant()));
                    ecrire(tcr0, 186, a.terminal());
                    ecrire(tcr0, 208, "952");
                    ecrire(tcr0, 211, "%012d".formatted(a.montant()));
                    ecrire(tcr0, 223, "952");
                    ecrire(tcr0, 226, "0");
                    ecrire(tcr0, 227, "%012d".formatted(a.interchangeCentimes()));
                    ecrire(tcr0, 239, "952");
                    ecrire(tcr0, 242, "0");
                    ecrire(tcr0, 243, a.interchangeCentimes() > 0 ? "C" : " ");
                }
            }
            StringBuilder fin = f.nouvelle(sf[1], "0");
            ecrire(fin, 10, "%06d".formatted(f.lignes.size() - debut));
        }
        return f.terminer();
    }

    private static final String[][] SOUS_FICHIERS = {{"92", "93"}, {"94", "95"}, {"96", "97"}, {"98", "99"}, {"80", "81"}};

    private void entete(String jjmmaa, String numeroFichier) {
        StringBuilder entete = nouvelle("90", "0");
        ecrire(entete, 10, "000343");
        ecrire(entete, 16, jjmmaa);
        ecrire(entete, 22, numeroFichier);
        ecrire(entete, 26, "3.038");
        ecrire(entete, 41, "101010");
    }

    private byte[] terminer() {
        StringBuilder fin = nouvelle("91", "0");
        ecrire(fin, 10, "%06d".formatted(lignes.size()));
        StringBuilder texte = new StringBuilder();
        lignes.forEach(l -> texte.append(l).append("\r\n"));
        return texte.toString().getBytes(StandardCharsets.ISO_8859_1);
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
