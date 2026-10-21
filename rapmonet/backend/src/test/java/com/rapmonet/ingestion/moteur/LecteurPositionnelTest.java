package com.rapmonet.ingestion.moteur;

import com.rapmonet.parametrage.AttributFormat;
import com.rapmonet.parametrage.FormatFichier;
import com.rapmonet.parametrage.Pad;
import com.rapmonet.parametrage.SousFichierFormat;
import com.rapmonet.parametrage.TcFormat;
import com.rapmonet.parametrage.TcrFormat;
import com.rapmonet.parametrage.TypeDonnee;
import com.rapmonet.parametrage.Zone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format réduit (longueur 40) inspiré du LIS : en-tête 90, fin 91 (nombre de lignes),
 * sous-fichier 98/99, TC 07 sur deux TCR (TCR 0 : carte ; TCR 1 : montant).
 */
class LecteurPositionnelTest {

    private static final int LG = 40;

    private static AttributFormat a(String code, int position, int taille, TypeDonnee type, boolean confidentiel) {
        return new AttributFormat(code, code, position, taille, Pad.ESPACE_DROITE, 0, type, true, confidentiel);
    }

    private static TcFormat tc(String code, TcrFormat... tcrs) {
        return new TcFormat(code, "TC " + code, null, LG, List.of(tcrs));
    }

    private static TcrFormat tcr(String code, AttributFormat... attributs) {
        return new TcrFormat(code, "TCR " + code, Integer.parseInt(code), List.of(attributs));
    }

    private static final FormatFichier FORMAT;

    static {
        Map<String, TcFormat> tcs = new LinkedHashMap<>();
        tcs.put("90", tc("90", tcr("0", a("LIS_DATE_JOURNEE", 10, 6, TypeDonnee.DATE_JJMMAA, false))));
        tcs.put("91", tc("91", tcr("0", a(LecteurPositionnel.NB_ENREGISTREMENTS, 10, 6, TypeDonnee.NUMERIQUE, false))));
        tcs.put("98", tc("98", tcr("0")));
        tcs.put("99", tc("99", tcr("0", a(LecteurPositionnel.NB_ENREGISTREMENTS_SOUS_FICHIER, 10, 6, TypeDonnee.NUMERIQUE, false))));
        tcs.put("07", tc("07",
                tcr("0", a("CARTE", 10, 19, TypeDonnee.ALPHA, true)),
                tcr("1", a("MONTANT", 10, 12, TypeDonnee.NUMERIQUE, false))));
        FORMAT = new FormatFichier("TEST", "LIS", "Test", LG, new Zone(1, 2), new Zone(3, 6), new Zone(9, 1), 10,
                "90", "91", List.of(new SousFichierFormat("FL", "FL", null, "98", "99", List.of("07"))), tcs);
    }

    private final LecteurPositionnel lecteur = new LecteurPositionnel(FORMAT, pan -> "H(" + pan + ")");

    /** Construit une ligne : TC, séquence, TCR puis données, complétée à la longueur du format. */
    private static String ligne(String tc, int sequence, String tcr, String donnees) {
        String l = tc + "%06d".formatted(sequence) + tcr + donnees;
        return l + " ".repeat(LG - l.length());
    }

    private static List<String> fichierValide() {
        List<String> l = new ArrayList<>();
        l.add(ligne("90", 1, "0", "100223"));
        l.add(ligne("98", 2, "0", ""));
        l.add(ligne("07", 3, "0", "4725370000517899"));
        l.add(ligne("07", 4, "1", "000000050000"));
        l.add(ligne("07", 5, "0", "5000000000000004"));
        l.add(ligne("07", 6, "1", "000000010000"));
        l.add(ligne("99", 7, "0", "000006"));
        l.add(ligne("91", 8, "0", "000008"));
        return l;
    }

    @Test
    void litUnFichierValide() {
        ResultatLecture r = lecteur.lire(fichierValide());

        assertThat(r.anomalies()).isEmpty();
        assertThat(r.entete()).containsEntry("LIS_DATE_JOURNEE", java.time.LocalDate.of(2023, 2, 10));
        assertThat(r.transactions()).hasSize(2);
        TransactionLue premiere = r.transactions().getFirst();
        assertThat(premiere.sousFichier()).isEqualTo("FL");
        assertThat(premiere.lignes()).hasSize(2);
        assertThat(premiere.attributsSpecifiques())
                .containsEntry("MONTANT", 50000L)
                .containsEntry("CARTE", "472537******7899")
                .containsEntry("CARTE_EMPREINTE", "H(4725370000517899)");
    }

    @Test
    void neConserveJamaisLeNumeroDeCarteEnClair() {
        ResultatLecture r = lecteur.lire(fichierValide());

        assertThat(r.lignes()).noneMatch(l -> l.contenu().contains("4725370000517899"));
        assertThat(r.lignes().get(2).contenu()).contains("472537******7899");
        assertThat(r.lignes().get(2).contenu()).hasSize(LG);
    }

    @Test
    void ignoreLesLignesVidesFinales() {
        List<String> lignes = new ArrayList<>(fichierValide());
        lignes.add("");
        assertThat(lecteur.lire(lignes).anomalies()).isEmpty();
    }

    @Test
    void ruptureDeSequenceEstBloquante() {
        List<String> lignes = fichierValide();
        lignes.set(3, ligne("07", 9, "1", "000000050000"));
        ResultatLecture r = lecteur.lire(lignes);
        assertThat(r.estBloquant()).isTrue();
        assertThat(r.anomalies()).anyMatch(a -> a.message().contains("Rupture de séquence"));
    }

    @Test
    void nombreDeLignesFauxEnFinDeFichierEstBloquant() {
        List<String> lignes = fichierValide();
        lignes.set(7, ligne("91", 8, "0", "000009"));
        ResultatLecture r = lecteur.lire(lignes);
        assertThat(r.estBloquant()).isTrue();
    }

    @Test
    void nombreDeLignesFauxEnFinDeSousFichierEstUnAvertissement() {
        List<String> lignes = fichierValide();
        lignes.set(6, ligne("99", 7, "0", "000012"));
        ResultatLecture r = lecteur.lire(lignes);
        assertThat(r.estBloquant()).isFalse();
        assertThat(r.anomalies()).singleElement().extracting(Anomalie::gravite).isEqualTo(Gravite.AVERTISSEMENT);
    }

    @Test
    void finDeFichierAbsenteEstBloquante() {
        List<String> lignes = fichierValide().subList(0, 7);
        assertThat(lecteur.lire(lignes).anomalies())
                .anyMatch(a -> a.gravite() == Gravite.BLOQUANTE && a.message().contains("Fin de fichier absente"));
    }

    @Test
    void sousFichierNonFermeEstBloquant() {
        List<String> lignes = new ArrayList<>(fichierValide());
        lignes.remove(6);
        for (int i = 6; i < lignes.size(); i++) {
            lignes.set(i, ligne("91", i + 1, "0", "000007"));
        }
        assertThat(lecteur.lire(lignes).anomalies())
                .anyMatch(a -> a.gravite() == Gravite.BLOQUANTE && a.message().contains("n'est pas fermé"));
    }

    @Test
    void tcInconnuEstUnAvertissement() {
        List<String> lignes = fichierValide();
        lignes.set(4, ligne("55", 5, "0", ""));
        lignes.set(5, ligne("55", 6, "1", ""));
        ResultatLecture r = lecteur.lire(lignes);
        assertThat(r.estBloquant()).isFalse();
        assertThat(r.anomalies()).anyMatch(a -> a.message().contains("TC 55 inconnu"));
        assertThat(r.transactions()).hasSize(2);
    }

    @Test
    void masquage() {
        assertThat(LecteurPositionnel.masquer("4725370000517899   ")).isEqualTo("472537******7899");
        assertThat(LecteurPositionnel.masquer("12345678")).isEqualTo("****5678");
        assertThat(LecteurPositionnel.masquer("   ")).isEmpty();
    }
}
