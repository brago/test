package com.rapmonet.commun.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Écrit un {@link Tableau} au format Excel (xlsx) ou PDF. */
public final class ExportTableau {

    private static final Locale FR = Locale.FRANCE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private ExportTableau() {
    }

    public static void ecrire(Tableau tableau, FormatExport format, OutputStream sortie) {
        switch (format) {
            case XLSX -> excel(tableau, sortie);
            case PDF -> pdf(tableau, sortie);
        }
    }

    // ---------------------------------------------------------------- Excel

    public static void excel(Tableau t, OutputStream sortie) {
        try (SXSSFWorkbook classeur = new SXSSFWorkbook(500)) {
            SXSSFSheet feuille = classeur.createSheet(nomFeuille(t.titre()));
            Font gras = classeur.createFont();
            gras.setBold(true);
            Font titre = classeur.createFont();
            titre.setBold(true);
            titre.setFontHeightInPoints((short) 14);

            CellStyle styleTitre = classeur.createCellStyle();
            styleTitre.setFont(titre);
            CellStyle styleEntete = classeur.createCellStyle();
            styleEntete.setFont(gras);
            styleEntete.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            styleEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleEntete.setBorderBottom(BorderStyle.THIN);
            var format = classeur.createDataFormat();
            CellStyle styleDate = classeur.createCellStyle();
            styleDate.setDataFormat(format.getFormat("dd/mm/yyyy"));
            CellStyle styleDateHeure = classeur.createCellStyle();
            styleDateHeure.setDataFormat(format.getFormat("dd/mm/yyyy hh:mm:ss"));
            CellStyle styleMontant = classeur.createCellStyle();
            styleMontant.setDataFormat(format.getFormat("#,##0.00"));
            CellStyle styleEntier = classeur.createCellStyle();
            styleEntier.setDataFormat(format.getFormat("#,##0"));

            int r = 0;
            Cell c = feuille.createRow(r++).createCell(0);
            c.setCellValue(t.titre());
            c.setCellStyle(styleTitre);
            for (String critere : t.criteres()) {
                feuille.createRow(r++).createCell(0).setCellValue(critere);
            }
            feuille.createRow(r++).createCell(0).setCellValue(piedDePage(t));
            r++;

            int ligneEntete = r;
            Row entete = feuille.createRow(r++);
            List<Tableau.Colonne> colonnes = t.colonnes();
            for (int i = 0; i < colonnes.size(); i++) {
                Cell e = entete.createCell(i);
                e.setCellValue(colonnes.get(i).libelle());
                e.setCellStyle(styleEntete);
                feuille.setColumnWidth(i, largeurExcel(colonnes.get(i)));
            }
            feuille.createFreezePane(0, r);

            int[] compteur = {r};
            t.lignes().accept(valeurs -> {
                Row ligne = feuille.createRow(compteur[0]++);
                for (int i = 0; i < colonnes.size() && i < valeurs.size(); i++) {
                    Object v = valeurs.get(i);
                    if (v == null) {
                        continue;
                    }
                    Cell cellule = ligne.createCell(i);
                    switch (colonnes.get(i).type()) {
                        case MONTANT -> nombre(cellule, v, styleMontant);
                        case ENTIER -> nombre(cellule, v, styleEntier);
                        case DATE -> {
                            LocalDate d = date(v);
                            if (d != null) {
                                cellule.setCellValue(d);
                                cellule.setCellStyle(styleDate);
                            } else {
                                cellule.setCellValue(v.toString());
                            }
                        }
                        case DATE_HEURE -> {
                            LocalDateTime d = dateHeure(v);
                            if (d != null) {
                                cellule.setCellValue(d);
                                cellule.setCellStyle(styleDateHeure);
                            } else {
                                cellule.setCellValue(v.toString());
                            }
                        }
                        case TEXTE -> cellule.setCellValue(v.toString());
                    }
                }
            });
            if (compteur[0] > ligneEntete + 1) {
                feuille.setAutoFilter(new CellRangeAddress(ligneEntete, compteur[0] - 1, 0, colonnes.size() - 1));
            }
            classeur.write(sortie);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void nombre(Cell cellule, Object v, CellStyle style) {
        if (v instanceof Tableau.MontantSens m) {
            cellule.setCellValue(m.signe().doubleValue());
            cellule.setCellStyle(style);
        } else if (v instanceof Number n) {
            cellule.setCellValue(n.doubleValue());
            cellule.setCellStyle(style);
        } else {
            cellule.setCellValue(v.toString());
        }
    }

    private static int largeurExcel(Tableau.Colonne c) {
        int caracteres = switch (c.type()) {
            case DATE -> 12;
            case DATE_HEURE -> 20;
            case ENTIER, MONTANT -> 16;
            case TEXTE -> Math.max(12, Math.min(60, Math.round(c.largeur() * 8)));
        };
        return Math.max(caracteres, c.libelle().length() + 2) * 256;
    }

    private static String nomFeuille(String titre) {
        String nom = titre.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return nom.length() > 31 ? nom.substring(0, 31) : nom;
    }

    // ---------------------------------------------------------------- PDF

    public static void pdf(Tableau t, OutputStream sortie) {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 28, 32);
        PdfWriter ecrivain = PdfWriter.getInstance(document, sortie);
        String pied = piedDePage(t);
        ecrivain.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document doc) {
                var police = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase(t.titre() + " – " + pied, police), doc.left(), doc.bottom() - 16, 0);
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                        new Phrase("Page " + writer.getPageNumber(), police), doc.right(), doc.bottom() - 16, 0);
            }
        });
        document.open();
        document.add(new Paragraph(t.titre(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        var policeCritere = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        for (String critere : t.criteres()) {
            document.add(new Paragraph(critere, policeCritere));
        }
        document.add(new Paragraph(" ", policeCritere));

        List<Tableau.Colonne> colonnes = t.colonnes();
        PdfPTable table = new PdfPTable(colonnes.size());
        table.setWidthPercentage(100);
        float[] largeurs = new float[colonnes.size()];
        for (int i = 0; i < largeurs.length; i++) {
            largeurs[i] = colonnes.get(i).largeur();
        }
        table.setWidths(largeurs);
        table.setHeaderRows(1);
        var policeEntete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
        for (Tableau.Colonne c : colonnes) {
            PdfPCell cellule = new PdfPCell(new Phrase(c.libelle(), policeEntete));
            cellule.setBackgroundColor(new Color(225, 230, 240));
            cellule.setHorizontalAlignment(alignement(c));
            cellule.setPadding(3);
            table.addCell(cellule);
        }
        var police = FontFactory.getFont(FontFactory.HELVETICA, 7);
        int[] nb = {0};
        t.lignes().accept(valeurs -> {
            nb[0]++;
            for (int i = 0; i < colonnes.size(); i++) {
                Object v = i < valeurs.size() ? valeurs.get(i) : null;
                PdfPCell cellule = new PdfPCell(new Phrase(texte(colonnes.get(i), v), police));
                cellule.setHorizontalAlignment(alignement(colonnes.get(i)));
                cellule.setPadding(2);
                cellule.setBorder(Rectangle.BOTTOM);
                cellule.setBorderColor(new Color(220, 220, 220));
                table.addCell(cellule);
            }
        });
        if (nb[0] == 0) {
            PdfPCell vide = new PdfPCell(new Phrase("Aucune donnée.", police));
            vide.setColspan(colonnes.size());
            table.addCell(vide);
        }
        document.add(table);
        document.add(new Paragraph(nb[0] + " ligne(s).", policeCritere));
        document.close();
    }

    private static int alignement(Tableau.Colonne c) {
        return c.type() == Tableau.Type.ENTIER || c.type() == Tableau.Type.MONTANT ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    /** Valeur formatée à la française (séparateur de milliers, dates JJ/MM/AAAA). */
    static String texte(Tableau.Colonne c, Object v) {
        if (v == null) {
            return "";
        }
        // Les espaces insécables du format français ne sont pas dans la police PDF standard.
        return brut(c, v).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    private static String brut(Tableau.Colonne c, Object v) {
        return switch (c.type()) {
            case MONTANT -> v instanceof Tableau.MontantSens m
                    ? montant(m.montant()) + (m.sens() == null ? "" : " " + m.sens())
                    : v instanceof Number n ? montant(n) : v.toString();
            case ENTIER -> v instanceof Tableau.MontantSens m ? NumberFormat.getIntegerInstance(FR).format(m.montant())
                    : v instanceof Number n ? NumberFormat.getIntegerInstance(FR).format(n) : v.toString();
            case DATE -> {
                LocalDate d = date(v);
                yield d != null ? DATE.format(d) : v.toString();
            }
            case DATE_HEURE -> {
                LocalDateTime d = dateHeure(v);
                yield d != null ? DATE_HEURE.format(d) : v.toString();
            }
            case TEXTE -> v.toString();
        };
    }

    private static String montant(Number n) {
        NumberFormat f = NumberFormat.getNumberInstance(FR);
        BigDecimal b = n instanceof BigDecimal bd ? bd : new BigDecimal(n.toString());
        // Précision d'origine conservée (315.00 → « 315,00 », 1430040 → « 1 430 040 »), au plus 2 décimales.
        f.setMinimumFractionDigits(Math.clamp(b.scale(), 0, 2));
        f.setMaximumFractionDigits(2);
        return f.format(b);
    }

    private static LocalDate date(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof String s && s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(s);
        }
        return null;
    }

    private static LocalDateTime dateHeure(Object v) {
        if (v instanceof OffsetDateTime o) {
            return o.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (v instanceof LocalDateTime d) {
            return d;
        }
        return null;
    }

    private static String piedDePage(Tableau t) {
        return "Édité le " + DATE_HEURE.format(LocalDateTime.now())
                + (t.auteur() != null ? " par " + t.auteur() : "") + " – RAPMONET";
    }
}
