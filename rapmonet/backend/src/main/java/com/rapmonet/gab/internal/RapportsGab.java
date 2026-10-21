package com.rapmonet.gab.internal;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
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
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Rapports PDF d'approvisionnement et d'arrêté, à faire signer (gabiste, saisie, validation). */
public final class RapportsGab {

    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font SOUS_TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font GRAS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font PETIT = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
    private static final Font ALERTE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(176, 0, 32));
    private static final Color FOND_ENTETE = new Color(225, 230, 240);

    private RapportsGab() {
    }

    public static byte[] appro(ApprosService.Appro a, String edite) {
        return document("Rapport d'approvisionnement GAB", "Appro " + a.reference(), edite, d -> {
            d.add(infos(
                    "Référence", a.reference(), "Statut", statut(a.statut()),
                    "GAB", a.terminal() + " – " + a.libelleGab(), "Agence", nul(a.agence()),
                    "Date de l'appro", a.dateAppro().format(DATE_HEURE), "Compte caisse", nul(a.compteCaisse()),
                    "Gabiste", a.gabiste(), "Réf. / bon de chargement", nul(a.referenceExterne()) + " / " + nul(a.numeroBon())));
            if (a.commentaire() != null) {
                d.add(new Paragraph("Commentaire : " + a.commentaire(), NORMAL));
            }
            d.add(sousTitre("Billetage"));
            PdfPTable t = table(new float[]{2, 3, 3, 4}, "Cassette", "Coupure", "Nombre de billets", "Montant (XOF)");
            for (ApprosService.Ligne l : a.billetage()) {
                ligne(t, false, "Cassette " + l.cassette() + " (type " + l.type() + ")", Montants.texte(l.coupure()),
                        String.valueOf(l.nombre()), Montants.texte(l.montant()));
            }
            ligne(t, true, "Total", "", String.valueOf(a.billetage().stream().mapToInt(ApprosService.Ligne::nombre).sum()),
                    Montants.texte(a.montant()));
            d.add(t);
            if (a.controleJournal() != null) {
                d.add(sousTitre("Contrôle avec le journal du GAB"));
                d.add(new Paragraph(a.controleJournal().message(),
                        "ECART".equals(a.controleJournal().statut()) ? ALERTE : NORMAL));
            }
            decision(d, a.statut(), a.saisiPar(), a.saisiLe(), a.decidePar(), a.decideLe(), a.motif());
            signatures(d, "Le gabiste", "Saisi par " + a.saisiPar(), "Validé par " + nul(a.decidePar()));
        });
    }

    public static byte[] arrete(ArretesService.Arrete a, String edite) {
        return document("Rapport d'arrêté GAB", "Arrêté " + a.reference(), edite, d -> {
            d.add(infos(
                    "Référence", a.reference(), "Statut", statut(a.statut()),
                    "GAB", a.terminal() + " – " + a.libelleGab(), "Agence", nul(a.agence()),
                    "Date de l'arrêté", a.dateArrete().format(DATE_HEURE), "Gabiste", a.gabiste(),
                    "Appro arrêté", a.approReference() + " du " + a.dateAppro().format(DATE_HEURE),
                    "(0) Montant de l'appro", Montants.texte(a.montantAppro()) + " XOF"));
            if (a.commentaire() != null) {
                d.add(new Paragraph("Commentaire : " + a.commentaire(), NORMAL));
            }
            d.add(sousTitre("Compteurs du journal GAB (JGA), par type de billet"));
            PdfPTable j = table(new float[]{3, 3, 3, 3, 4}, "Billets", "(1) En cassette", "(2) Rejets / retraits",
                    "Total billets", "Montant (XOF)");
            for (ArretesService.LigneJournal l : a.journal()) {
                ligne(j, false, Montants.texte(l.coupure()) + " (type " + l.type() + ")", String.valueOf(l.cassette()),
                        String.valueOf(l.rejet()), String.valueOf(l.cassette() + l.rejet()), Montants.texte(l.montant()));
            }
            ligne(j, true, "(5) = (1) + (2)", "", "", "", Montants.texte(a.montantJournal()));
            d.add(j);

            d.add(sousTitre("Inventaire physique, par cassette"));
            PdfPTable p = table(new float[]{3, 3, 3, 3, 4}, "Cassette", "(3) En cassette", "(4) Rejets",
                    "Total billets", "Montant (XOF)");
            for (ArretesService.LigneInventaire l : a.inventaire()) {
                ligne(p, false, l.cassette() + " – " + Montants.texte(l.coupure()), String.valueOf(l.nombre()),
                        String.valueOf(l.rejet()), String.valueOf(l.nombre() + l.rejet()), Montants.texte(l.montant()));
            }
            ligne(p, true, "(6) = (3) + (4)", "", "", "", Montants.texte(a.montantPhysique()));
            d.add(p);

            d.add(sousTitre("Soldes et écarts"));
            PdfPTable s = table(new float[]{8, 4}, "Rubrique", "Montant (XOF)");
            ligne(s, false, "(5) Solde selon le journal", Montants.texte(a.montantJournal()));
            ligne(s, false, "(6) Solde physique", Montants.texte(a.montantPhysique()));
            ligne(s, true, "Écart physique - journal : (6) - (5)" + sens(a.ecartPhysiqueJournal()),
                    Montants.texte(a.ecartPhysiqueJournal()));
            ligne(s, false, "(7) Transactions en attente", Montants.texte(a.transactionsAttente()));
            ligne(s, false, "(8) Solde comptable", a.soldeComptable() == null ? "non saisi" : Montants.texte(a.soldeComptable()));
            ligne(s, true, "Écart physique - comptable : (6) + (7) - (8)"
                            + (a.ecartPhysiqueComptable() == null ? "" : sens(a.ecartPhysiqueComptable())),
                    a.ecartPhysiqueComptable() == null ? "–" : Montants.texte(a.ecartPhysiqueComptable()));
            if (a.retraitsJournal() != null) {
                ligne(s, false, "Retraits distribués selon les journaux intégrés depuis l'appro", Montants.texte(a.retraitsJournal()));
                ligne(s, false, "Solde théorique : (0) - retraits", Montants.texte(a.soldeTheorique()));
                ligne(s, false, "Écart journal - théorique : (5) - solde théorique", Montants.texte(a.ecartJournalTheorique()));
                if (a.couverture() != null) {
                    ligne(s, false, "Journaux intégrés sur la période", a.couverture().joursCouverts() + " jour(s) sur "
                            + a.couverture().joursPeriode()
                            + (a.couverture().joursCouverts() < a.couverture().joursPeriode() ? " : solde théorique incomplet" : ""));
                }
            }
            d.add(s);
            if (a.ecartPhysiqueComptable() != null && a.ecartPhysiqueComptable() != 0) {
                d.add(new Paragraph((a.ecartPhysiqueComptable() < 0 ? "Manquant à imputer au compte " + nul(a.compteManquant())
                        : "Excédent à imputer au compte " + nul(a.compteExcedent())) + ".", ALERTE));
            }
            decision(d, a.statut(), a.saisiPar(), a.saisiLe(), a.decidePar(), a.decideLe(), a.motif());
            signatures(d, "Le gabiste", "Saisi par " + a.saisiPar(), "Validé par " + nul(a.decidePar()));
        });
    }

    // ------------------------------------------------------------------ mise en page

    private interface Contenu {
        void ecrire(Document d);
    }

    private static byte[] document(String titre, String objet, String edite, Contenu contenu) {
        var sortie = new ByteArrayOutputStream();
        Document d = new Document(PageSize.A4, 36, 36, 36, 40);
        PdfWriter w = PdfWriter.getInstance(d, sortie);
        String pied = "RAPMONET – " + objet + " – édité le " + LocalDateTime.now().format(DATE_HEURE) + " par " + edite;
        w.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document doc) {
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase(pied, PETIT),
                        doc.left(), doc.bottom() - 18, 0);
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                        new Phrase("Page " + writer.getPageNumber(), PETIT), doc.right(), doc.bottom() - 18, 0);
            }
        });
        d.open();
        Paragraph t = new Paragraph(titre, TITRE);
        t.setSpacingAfter(10);
        d.add(t);
        contenu.ecrire(d);
        d.close();
        return sortie.toByteArray();
    }

    /** Tableau d'informations sur deux colonnes : libellé / valeur, libellé / valeur. */
    private static PdfPTable infos(String... paires) {
        PdfPTable t = new PdfPTable(new float[]{3, 5, 3, 5});
        t.setWidthPercentage(100);
        for (int i = 0; i < paires.length; i += 2) {
            t.addCell(cellule(paires[i], GRAS, Element.ALIGN_LEFT, null, Rectangle.NO_BORDER));
            t.addCell(cellule(paires[i + 1], NORMAL, Element.ALIGN_LEFT, null, Rectangle.NO_BORDER));
        }
        return t;
    }

    private static Paragraph sousTitre(String texte) {
        Paragraph p = new Paragraph(texte, SOUS_TITRE);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    private static PdfPTable table(float[] largeurs, String... entetes) {
        PdfPTable t = new PdfPTable(largeurs);
        t.setWidthPercentage(100);
        for (int i = 0; i < entetes.length; i++) {
            t.addCell(cellule(entetes[i], GRAS, i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, FOND_ENTETE, Rectangle.BOX));
        }
        return t;
    }

    private static void ligne(PdfPTable t, boolean total, String... valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            t.addCell(cellule(valeurs[i], total ? GRAS : NORMAL, i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT,
                    total ? new Color(245, 245, 245) : null, Rectangle.BOX));
        }
    }

    private static PdfPCell cellule(String texte, Font police, int alignement, Color fond, int bordure) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, police));
        c.setHorizontalAlignment(alignement);
        c.setPadding(4);
        c.setBorder(bordure);
        c.setBorderColor(new Color(200, 200, 200));
        if (fond != null) {
            c.setBackgroundColor(fond);
        }
        return c;
    }

    private static void decision(Document d, String statut, String saisiPar, OffsetDateTime saisiLe, String decidePar,
                                 OffsetDateTime decideLe, String motif) {
        d.add(sousTitre("Suivi"));
        d.add(new Paragraph("Saisi par " + saisiPar + " le " + date(saisiLe) + ".", NORMAL));
        if ("VALIDE".equals(statut)) {
            d.add(new Paragraph("Validé par " + decidePar + " le " + date(decideLe) + ".", NORMAL));
        } else if ("ANNULE".equals(statut)) {
            d.add(new Paragraph("Annulé par " + decidePar + " le " + date(decideLe) + " : " + motif, ALERTE));
        } else {
            d.add(new Paragraph("En attente de validation.", ALERTE));
        }
    }

    private static void signatures(Document d, String... roles) {
        d.add(sousTitre("Signatures"));
        PdfPTable t = new PdfPTable(roles.length);
        t.setWidthPercentage(100);
        for (String r : roles) {
            t.addCell(cellule(r, GRAS, Element.ALIGN_CENTER, FOND_ENTETE, Rectangle.BOX));
        }
        for (int i = 0; i < roles.length; i++) {
            PdfPCell vide = cellule(" ", NORMAL, Element.ALIGN_CENTER, null, Rectangle.BOX);
            vide.setFixedHeight(60);
            t.addCell(vide);
        }
        d.add(t);
    }

    private static String sens(long ecart) {
        return ecart < 0 ? " – manquant" : ecart > 0 ? " – excédent" : "";
    }

    private static String statut(String s) {
        return switch (s) {
            case "VALIDE" -> "Validé";
            case "ANNULE" -> "Annulé";
            default -> "Saisi, en attente de validation";
        };
    }

    private static String date(OffsetDateTime d) {
        return d == null ? "" : d.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_HEURE);
    }

    private static String nul(String s) {
        return s == null ? "–" : s;
    }
}
