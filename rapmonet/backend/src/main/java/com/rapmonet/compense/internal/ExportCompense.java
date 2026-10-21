package com.rapmonet.compense.internal;

import com.rapmonet.compense.internal.CompenseService.Ecriture;
import com.rapmonet.compense.internal.CompenseService.Ligne;
import com.rapmonet.compense.internal.CompenseService.Traitement;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Classeur Excel identique à la sortie de la macro : pièces par réseau, MACRO (import Amplitude), RÉCAP. */
public final class ExportCompense {

    private static final DateTimeFormatter JJMMAAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] ENTETE_PIECE = {"CPTE A DBT", "NOMS COMMERCANTS", "N COMPTE", "MONTANTS", "CCI",
            "COMM ACQUI", "CPTE COM", "TAF", "CPTE TAF", "C_AUTHO"};
    private static final String[] ENTETE_MACRO = {"COMPTE", "DATOPER", "DATVAL", "MNTFRF", "MNTDEV", "LIBELLE", "CODOPSC",
            "CODECO", "FIGNE", "DEVISE", "EXPL", "FLAG", "NOOPER", "NATOP", "REFREL", "VALIDE", "DATHGEN", "NOREF", "XCASH",
            "DATCPTA"};

    private ExportCompense() {
    }

    public static byte[] excel(Traitement t) {
        try (XSSFWorkbook c = new XSSFWorkbook(); var sortie = new ByteArrayOutputStream()) {
            Styles st = new Styles(c);
            String libDate = CompenseService.libelleDate(t.journee());
            piece(c, st, "PIECE GIM", "GIM UEMOA - " + libDate, t, "GIM");
            piece(c, st, "PIECE VISA", "VISA - " + libDate, t, "VISA");
            piece(c, st, "PIECE MASTERCARD", "MASTERCARD - " + libDate, t, "MASTERCARD");
            macro(c, st, t, libDate);
            recap(c, st, t, libDate);
            c.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void piece(XSSFWorkbook c, Styles st, String nom, String titre, Traitement t, String reseau) {
        Sheet s = c.createSheet(nom);
        titre(s, st, "TRAITEMENT COMPENSE TPE DU " + titre, ENTETE_PIECE.length);
        entete(s, st, ENTETE_PIECE);
        int r = 2;
        List<Ligne> lignes = t.lignes().stream().filter(l -> l.reseau().equals(reseau)).toList();
        for (Ligne l : lignes) {
            Row row = s.createRow(r++);
            texte(row, 0, l.compteDebit());
            texte(row, 1, l.commercant());
            texte(row, 2, l.compte());
            nombre(row, 3, l.montant(), st.entier);
            nombre(row, 4, l.frais(), st.entier);
            nombre(row, 5, l.commission(), st.entier);
            texte(row, 6, t.parametres().compteCommission());
            nombre(row, 7, l.taf(), st.entier);
            texte(row, 8, t.parametres().compteTaf());
            texte(row, 9, l.autorisation());
        }
        Row tot = s.createRow(r);
        texte(tot, 0, "TOTAUX").setCellStyle(st.gras);
        nombre(tot, 3, lignes.stream().mapToLong(Ligne::montant).sum(), st.totalEntier);
        nombre(tot, 4, lignes.stream().mapToLong(Ligne::frais).sum(), st.totalEntier);
        nombre(tot, 5, lignes.stream().mapToLong(Ligne::commission).sum(), st.totalEntier);
        nombre(tot, 7, lignes.stream().mapToLong(Ligne::taf).sum(), st.totalEntier);
        int[] largeurs = {16, 40, 16, 14, 14, 14, 16, 10, 16, 12};
        for (int i = 0; i < largeurs.length; i++) {
            s.setColumnWidth(i, largeurs[i] * 256);
        }
        s.createFreezePane(0, 2);
    }

    private static void macro(XSSFWorkbook c, Styles st, Traitement t, String libDate) {
        Sheet s = c.createSheet("MACRO");
        titre(s, st, "MACRO COMPTABLE - " + libDate, 6);
        entete(s, st, ENTETE_MACRO);
        int r = 2;
        for (Ecriture e : t.ecritures()) {
            Row row = s.createRow(r++);
            texte(row, 0, e.compte());
            texte(row, 1, JJMMAAAA.format(e.dateOperation()));
            texte(row, 2, JJMMAAAA.format(e.dateValeur()));
            nombre(row, 3, e.montant(), st.entier);
            nombre(row, 4, e.montant(), st.entier);
            texte(row, 5, e.libelle());
            texte(row, 6, t.parametres().codeOperation());
            texte(row, 9, t.parametres().devise());
        }
        int[] largeurs = {14, 12, 12, 14, 14, 70, 10, 7, 6, 7, 6, 6, 8, 7, 7, 7, 10, 7, 7, 10};
        for (int i = 0; i < largeurs.length; i++) {
            s.setColumnWidth(i, largeurs[i] * 256);
        }
        s.createFreezePane(0, 2);
    }

    private static void recap(XSSFWorkbook c, Styles st, Traitement t, String libDate) {
        Sheet s = c.createSheet("RÉCAP");
        titre(s, st, "RÉCAPITULATIF — TRAITEMENT COMPENSÉ TPE — " + libDate + " (réf. " + t.reference() + ")", 8);
        entete(s, st, new String[]{"RÉSEAU", "NB TRANSACTIONS", "MONTANT TOTAL (XOF)", "COMM ACQUI (XOF)", "TAF (XOF)",
                "CCI TOTAL (XOF)", "COMPTE DÉBIT", "COMPTE CRÉDIT"});
        int r = 2;
        for (var x : t.totaux()) {
            Row row = s.createRow(r++);
            texte(row, 0, x.categorie());
            nombre(row, 1, x.nombre(), st.entier);
            nombre(row, 2, x.montant(), st.entier);
            nombre(row, 3, x.commission(), st.entier);
            nombre(row, 4, x.taf(), st.entier);
            nombre(row, 5, x.frais(), st.entier);
            texte(row, 6, x.compteDebit());
            texte(row, 7, x.compteCredit());
        }
        Row tot = s.createRow(r++);
        texte(tot, 0, "TOTAL").setCellStyle(st.gras);
        nombre(tot, 1, t.totaux().stream().mapToLong(CompenseService.TotalReseau::nombre).sum(), st.totalEntier);
        nombre(tot, 2, t.totaux().stream().mapToLong(CompenseService.TotalReseau::montant).sum(), st.totalEntier);
        nombre(tot, 3, t.totaux().stream().mapToLong(CompenseService.TotalReseau::commission).sum(), st.totalEntier);
        nombre(tot, 4, t.totaux().stream().mapToLong(CompenseService.TotalReseau::taf).sum(), st.totalEntier);
        nombre(tot, 5, t.totaux().stream().mapToLong(CompenseService.TotalReseau::frais).sum(), st.totalEntier);
        r++;
        Row sous = s.createRow(r++);
        texte(sous, 0, "COMPTES MOUVEMENTÉS").setCellStyle(st.gras);
        Row e = s.createRow(r++);
        String[] h = {"COMPTE", "LIBELLÉ", "DÉBIT (XOF)", "CRÉDIT (XOF)", "SOLDE NET (XOF)"};
        for (int i = 0; i < h.length; i++) {
            texte(e, i, h[i]).setCellStyle(st.entete);
        }
        for (var m : t.comptes()) {
            Row row = s.createRow(r++);
            texte(row, 0, m.compte());
            texte(row, 1, m.libelle());
            nombre(row, 2, m.debit(), st.entier);
            nombre(row, 3, m.credit(), st.entier);
            nombre(row, 4, m.credit() - m.debit(), st.entier);
        }
        for (int i = 0; i < 8; i++) {
            s.setColumnWidth(i, (i == 1 ? 30 : 18) * 256);
        }
    }

    private static void titre(Sheet s, Styles st, String texte, int colonnes) {
        Row r = s.createRow(0);
        texte(r, 0, texte).setCellStyle(st.titre);
        r.setHeightInPoints(26);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, colonnes - 1));
    }

    private static void entete(Sheet s, Styles st, String[] colonnes) {
        Row r = s.createRow(1);
        for (int i = 0; i < colonnes.length; i++) {
            texte(r, i, colonnes[i]).setCellStyle(st.entete);
        }
    }

    private static org.apache.poi.ss.usermodel.Cell texte(Row r, int i, String v) {
        var cell = r.createCell(i);
        if (v != null) {
            cell.setCellValue(v);
        }
        return cell;
    }

    private static void nombre(Row r, int i, double v, CellStyle style) {
        var cell = r.createCell(i);
        cell.setCellValue(v);
        cell.setCellStyle(style);
    }

    private static final class Styles {
        final CellStyle titre;
        final CellStyle entete;
        final CellStyle gras;
        final CellStyle entier;
        final CellStyle totalEntier;

        Styles(XSSFWorkbook c) {
            Font blanc = c.createFont();
            blanc.setBold(true);
            blanc.setColor(IndexedColors.WHITE.getIndex());
            Font grasF = c.createFont();
            grasF.setBold(true);
            titre = c.createCellStyle();
            titre.setFont(blanc);
            titre.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            titre.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            entete = c.createCellStyle();
            entete.setFont(blanc);
            entete.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            entete.setBorderBottom(BorderStyle.THIN);
            gras = c.createCellStyle();
            gras.setFont(grasF);
            var format = c.createDataFormat();
            entier = c.createCellStyle();
            entier.setDataFormat(format.getFormat("#,##0"));
            totalEntier = c.createCellStyle();
            totalEntier.cloneStyleFrom(entier);
            totalEntier.setFont(grasF);
        }
    }
}
