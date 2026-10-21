package com.rapmonet.commun.export;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportTableauTest {

    private static final List<Tableau.Colonne> COLONNES = List.of(
            new Tableau.Colonne("Date", Tableau.Type.DATE, 5),
            new Tableau.Colonne("Montant", Tableau.Type.MONTANT, 5),
            new Tableau.Colonne("Libellé", Tableau.Type.TEXTE, 10));

    private static Tableau tableau() {
        return new Tableau("Essai", List.of("Période : test"), "admin", COLONNES, ecrire -> {
            ecrire.accept(Arrays.asList(LocalDate.of(2023, 2, 10), 50000L, "GAB été"));
            ecrire.accept(Arrays.asList("2023-02-11", new BigDecimal("12.50"), null));
        });
    }

    @Test
    void excelContientEnteteEtValeursTypees() throws Exception {
        var sortie = new ByteArrayOutputStream();
        ExportTableau.excel(tableau(), sortie);
        try (var classeur = new XSSFWorkbook(new ByteArrayInputStream(sortie.toByteArray()))) {
            var feuille = classeur.getSheetAt(0);
            assertThat(feuille.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Essai");
            int entete = 4;
            assertThat(feuille.getRow(entete).getCell(1).getStringCellValue()).isEqualTo("Montant");
            assertThat(feuille.getRow(entete + 1).getCell(1).getNumericCellValue()).isEqualTo(50000);
            assertThat(feuille.getRow(entete + 1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2023, 2, 10));
            assertThat(feuille.getRow(entete + 2).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2023, 2, 11));
            assertThat(feuille.getRow(entete + 1).getCell(2).getStringCellValue()).isEqualTo("GAB été");
        }
    }

    @Test
    void pdfEstGenere() {
        var sortie = new ByteArrayOutputStream();
        ExportTableau.pdf(tableau(), sortie);
        assertThat(new String(sortie.toByteArray(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void formatageFrancaisSansEspaceInsecable() {
        assertThat(ExportTableau.texte(COLONNES.get(1), 1234567L)).isEqualTo("1 234 567");
        assertThat(ExportTableau.texte(COLONNES.get(1), new BigDecimal("12.50"))).isEqualTo("12,50");
        assertThat(ExportTableau.texte(COLONNES.get(1), new BigDecimal("315.00"))).isEqualTo("315,00");
        assertThat(ExportTableau.texte(COLONNES.get(1), new Tableau.MontantSens(new BigDecimal("2183.96"), "DB"))).isEqualTo("2 183,96 DB");
        assertThat(ExportTableau.texte(COLONNES.get(0), "2023-02-10")).isEqualTo("10/02/2023");
    }
}
