package com.rapmonet.ingestion.vss;

import com.rapmonet.ingestion.moteur.Anomalie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lit un fichier de rapports VisaNet Settlement Service imprimés (EP-747, renommé « VX » par le GIM).
 *
 * <p>Le fichier est un état d'impression : le premier caractère de chaque ligne est un code de
 * contrôle (saut de page « \f » ou « 1 »), chaque page commence par « REPORT ID: », suivi
 * d'un en-tête (SRE, dates, devises), des titres de colonnes puis des lignes de libellés et de
 * montants. Le lecteur est générique : les colonnes sont déduites des titres de colonnes
 * (positions horizontales) et la hiérarchie des libellés de leur indentation.
 */
public class LecteurRapportsVss {

    /** Au-delà de cette indentation, une ligne sans valeur est un titre de colonnes (en-tête de page). */
    static final int INDENTATION_MAX_LIBELLE = 25;

    private static final Pattern REPORT_ID = Pattern.compile("^REPORT ID:\\s+(\\S+).*?PAGE:\\s+(\\d+)");
    private static final Pattern CLE_ENTETE = Pattern.compile(
            "^\\s?(REPORTING FOR|ROLLUP TO|FUNDS XFER ENTITY|SETTLEMENT CURRENCY|CLEARING CURRENCY):\\s*(.*)$");
    private static final Pattern PROC_DATE = Pattern.compile("PROC DATE:\\s+(\\w+)");
    private static final Pattern REPORT_DATE = Pattern.compile("REPORT DATE:\\s+(\\w+)");
    private static final Pattern SEGMENT = Pattern.compile("\\S+(?: \\S+)*");
    private static final Pattern NOMBRE = Pattern.compile("^(-?[\\d,]*\\.?\\d+)(DB|CR)?$");
    private static final Pattern BANNIERE = Pattern.compile("\\*{2,}([A-Z][A-Z ]*?)\\*{2,}");
    private static final DateTimeFormatter DATE_VSS = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("ddMMMyy").toFormatter(Locale.ENGLISH);

    public ResultatLectureVss lire(List<String> lignesBrutes) {
        return new Lecture().executer(lignesBrutes);
    }

    static LocalDate date(String texte) {
        try {
            return LocalDate.parse(texte, DATE_VSS);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Nombre au format VSS (« 1,430,040DB », « 315.00CR », « 20 ») ; null si ce n'est pas un nombre. */
    static ValeurVss valeur(String texte) {
        Matcher m = NOMBRE.matcher(texte);
        if (!m.matches()) {
            return new ValeurVss(null, null, texte);
        }
        return new ValeurVss(new BigDecimal(m.group(1).replace(",", "")), m.group(2), null);
    }

    private record Segment(int debut, int fin, String texte) {
        boolean estNombre() {
            return NOMBRE.matcher(texte).matches();
        }
    }

    private static List<Segment> segments(String ligne) {
        List<Segment> l = new ArrayList<>();
        Matcher m = SEGMENT.matcher(ligne);
        while (m.find()) {
            l.add(new Segment(m.start(), m.end() - 1, m.group()));
        }
        return l;
    }

    /** Colonne déduite des titres : plage horizontale et nom. */
    private static final class Colonne {
        int debut;
        int fin;
        final List<String> mots = new ArrayList<>();
        String groupe;

        Colonne(Segment s) {
            debut = s.debut();
            fin = s.fin();
            mots.add(s.texte());
        }

        int chevauchement(int d, int f) {
            return Math.min(fin, f) - Math.max(debut, d) + 1;
        }

        String nom() {
            String n = String.join(" ", mots);
            return groupe == null ? n : n + " (" + groupe + ")";
        }
    }

    private final class Lecture {
        private final List<RapportVss> rapports = new ArrayList<>();
        private final List<Anomalie> anomalies = new ArrayList<>();

        // Rapport en cours
        private String code;
        private String titre;
        private String sreId;
        private String sreNom;
        private String rollup;
        private String entiteFonds;
        private LocalDate dateTraitement;
        private LocalDate dateRapport;
        private int nbPages;
        private boolean sansDonnees;
        private int ligneDebut;
        private List<Colonne> colonnes;
        private List<LigneVss> lignes;
        private final Deque<Object[]> pile = new ArrayDeque<>(); // {indentation, libellé}
        private String dernierLibelle;
        private String dernierChemin;
        private int dernierNiveau;

        // Page en cours
        private int page;
        private boolean enEntete;
        private String deviseReglement;
        private String deviseCompensation;
        private List<Colonne> colonnesPage;
        private List<Object[]> bannieres;

        ResultatLectureVss executer(List<String> brutes) {
            for (int i = 0; i < brutes.size(); i++) {
                String brute = brutes.get(i);
                if (brute.isEmpty()) {
                    continue;
                }
                char controle = brute.charAt(0);
                String ligne = brute.substring(1).stripTrailing();
                boolean nouvellePage = controle == '\f' || controle == '1';
                if (nouvellePage || ligne.startsWith("REPORT ID:")) {
                    debuterPage(i + 1, ligne);
                } else if (code != null) {
                    traiter(i + 1, ligne);
                }
            }
            if (code != null) {
                anomalies.add(Anomalie.bloquante(null,
                        "Fichier tronqué : le rapport %s n'a pas de ligne de fin (END OF REPORT).".formatted(code)));
                cloturer(false);
            }
            if (rapports.isEmpty() && anomalies.isEmpty()) {
                anomalies.add(Anomalie.bloquante(null, "Aucun rapport VSS trouvé (ligne « REPORT ID: » absente)."));
            }
            long dates = rapports.stream().map(RapportVss::dateTraitement).filter(java.util.Objects::nonNull).distinct().count();
            if (dates > 1) {
                anomalies.add(Anomalie.avertissement(null, "Les rapports portent sur plusieurs dates de traitement : "
                        + rapports.stream().map(r -> String.valueOf(r.dateTraitement())).distinct().collect(Collectors.joining(", "))));
            }
            return new ResultatLectureVss(List.copyOf(rapports), List.copyOf(anomalies));
        }

        private void debuterPage(int numero, String ligne) {
            Matcher m = REPORT_ID.matcher(ligne);
            if (!m.find()) {
                anomalies.add(Anomalie.avertissement(numero, "Nouvelle page sans « REPORT ID: » : ligne ignorée."));
                return;
            }
            String nouveauCode = m.group(1);
            if (code != null && !code.equals(nouveauCode)) {
                anomalies.add(Anomalie.avertissement(ligneDebut,
                        "Le rapport %s n'a pas de ligne de fin (END OF REPORT).".formatted(code)));
                cloturer(false);
            }
            if (code == null) {
                code = nouveauCode;
                ligneDebut = numero;
                titre = sreId = sreNom = rollup = entiteFonds = null;
                dateTraitement = dateRapport = null;
                nbPages = 0;
                sansDonnees = false;
                colonnes = null;
                lignes = new ArrayList<>();
                pile.clear();
                dernierLibelle = dernierChemin = null;
            }
            nbPages++;
            page = Integer.parseInt(m.group(2));
            enEntete = true;
            deviseReglement = deviseCompensation = null;
            colonnesPage = new ArrayList<>();
            bannieres = new ArrayList<>();
        }

        private void traiter(int numero, String ligne) {
            if (ligne.isBlank()) {
                return;
            }
            if (ligne.contains("***")) {
                if (ligne.contains("NO DATA")) {
                    sansDonnees = true;
                } else if (ligne.contains("END OF")) {
                    cloturer(true);
                } else if (enEntete) {
                    Matcher b = BANNIERE.matcher(ligne);
                    while (b.find()) {
                        bannieres.add(new Object[]{b.start(), b.end() - 1, b.group(1).strip()});
                    }
                }
                return;
            }
            if (enEntete && entete(ligne)) {
                return;
            }
            if (enEntete) {
                List<Segment> segs = segments(ligne);
                int indentation = segs.getFirst().debut();
                if (indentation > INDENTATION_MAX_LIBELLE && segs.stream().noneMatch(Segment::estNombre)) {
                    segs.forEach(this::ajouterTitreColonne);
                    return;
                }
                finEntete();
            }
            corps(numero, ligne);
        }

        /** Ligne d'en-tête de page (SRE, dates, devises, titre du rapport) ; faux sinon. */
        private boolean entete(String ligne) {
            Matcher d = PROC_DATE.matcher(ligne);
            if (d.find()) {
                dateTraitement = date(d.group(1));
            }
            Matcher r = REPORT_DATE.matcher(ligne);
            if (r.find()) {
                dateRapport = date(r.group(1));
                if (titre == null) {
                    titre = segments(ligne).stream().map(Segment::texte)
                            .filter(t -> t.endsWith("REPORT") && !t.startsWith("REPORT DATE"))
                            .findFirst().orElse(null);
                }
            }
            Matcher c = CLE_ENTETE.matcher(ligne);
            if (!c.find()) {
                return d.find(0) || r.find(0);
            }
            String valeur = c.group(2).split("\\s{2,}")[0].strip();
            String suite = c.group(2).strip();
            switch (c.group(1)) {
                case "REPORTING FOR" -> {
                    String[] p = valeur.split(" ", 2);
                    sreId = p[0];
                    String reste = suite.substring(p[0].length()).strip();
                    sreNom = reste.split("\\s{2,}")[0];
                }
                case "ROLLUP TO" -> rollup = valeur;
                case "FUNDS XFER ENTITY" -> entiteFonds = valeur;
                case "SETTLEMENT CURRENCY" -> deviseReglement = valeur;
                case "CLEARING CURRENCY" -> deviseCompensation = valeur;
                default -> { }
            }
            return true;
        }

        private void ajouterTitreColonne(Segment s) {
            Colonne meilleure = null;
            int max = 0;
            for (Colonne c : colonnesPage) {
                int ch = c.chevauchement(s.debut(), s.fin());
                if (ch > max) {
                    max = ch;
                    meilleure = c;
                }
            }
            if (meilleure == null) {
                colonnesPage.add(new Colonne(s));
            } else {
                meilleure.mots.add(s.texte());
                meilleure.debut = Math.min(meilleure.debut, s.debut());
                meilleure.fin = Math.max(meilleure.fin, s.fin());
            }
        }

        private void finEntete() {
            enEntete = false;
            colonnesPage.sort((a, b) -> Integer.compare(a.debut, b.debut));
            for (Colonne c : colonnesPage) {
                for (Object[] b : bannieres) {
                    if (c.chevauchement((int) b[0], (int) b[1]) > 0) {
                        c.groupe = (String) b[2];
                    }
                }
            }
            if (colonnes == null && !colonnesPage.isEmpty()) {
                colonnes = colonnesPage;
            }
        }

        private void corps(int numero, String ligne) {
            List<Segment> segs = segments(ligne);
            Segment premier = segs.getFirst();
            boolean avecLibelle = premier.debut() <= INDENTATION_MAX_LIBELLE && !premier.estNombre();
            int nbSegmentsLibelle = 0;
            if (avecLibelle) {
                // Un libellé peut contenir plusieurs espaces : « NET   PURCHASE ».
                StringBuilder texte = new StringBuilder();
                while (nbSegmentsLibelle < segs.size() && !segs.get(nbSegmentsLibelle).estNombre()
                        && segs.get(nbSegmentsLibelle).debut() <= INDENTATION_MAX_LIBELLE) {
                    texte.append(texte.isEmpty() ? "" : " ").append(segs.get(nbSegmentsLibelle).texte());
                    nbSegmentsLibelle++;
                }
                Segment dernier = segs.get(nbSegmentsLibelle - 1);
                premier = new Segment(premier.debut(), dernier.fin(), texte.toString());
            }
            List<Segment> valeurs = segs.subList(nbSegmentsLibelle, segs.size());

            String libelle;
            String chemin;
            int niveau;
            if (avecLibelle) {
                while (!pile.isEmpty() && (int) pile.peek()[0] >= premier.debut()) {
                    pile.pop();
                }
                chemin = cheminCourant();
                niveau = pile.size();
                pile.push(new Object[]{premier.debut(), premier.texte()});
                libelle = premier.texte();
                dernierLibelle = libelle;
                dernierChemin = chemin;
                dernierNiveau = niveau;
            } else {
                // Ligne de valeurs sans libellé : suite du libellé précédent (ex. autre table de taux).
                libelle = dernierLibelle;
                chemin = dernierChemin;
                niveau = dernierNiveau;
            }
            if (valeurs.isEmpty()) {
                // Titre de section (ex. « INTERCHANGE VALUE ») : conservé sans valeur pour la lisibilité.
                lignes.add(new LigneVss(numero, page, niveau, chemin, libelle, deviseReglement, deviseCompensation, Map.of()));
                return;
            }
            List<Colonne> cols = colonnes == null ? List.of() : colonnes;
            Map<String, ValeurVss> parColonne = new LinkedHashMap<>();
            for (Segment v : valeurs) {
                String nom = colonne(cols, v);
                if (parColonne.containsKey(nom)) {
                    anomalies.add(Anomalie.avertissement(numero, "Rapport %s : deux valeurs pour la colonne « %s »."
                            .formatted(code, nom)));
                    nom = nom + " (" + (v.debut() + 2) + ")";
                }
                parColonne.put(nom, valeur(v.texte()));
            }
            LigneVss precedente = lignes.isEmpty() ? null : lignes.getLast();
            if (!avecLibelle && precedente != null && precedente.valeurs().isEmpty() && libelle != null
                    && libelle.equals(precedente.libelle())) {
                // Libellé seul sur une ligne, valeurs sur la suivante (VSS-210) : une seule ligne.
                lignes.set(lignes.size() - 1, new LigneVss(precedente.numeroLigne(), page, niveau, chemin, libelle,
                        deviseReglement, deviseCompensation, parColonne));
                return;
            }
            lignes.add(new LigneVss(numero, page, niveau, chemin, libelle, deviseReglement, deviseCompensation, parColonne));
        }

        private String colonne(List<Colonne> cols, Segment v) {
            Colonne meilleure = null;
            int max = 0;
            for (Colonne c : cols) {
                int ch = c.chevauchement(v.debut(), v.fin());
                if (ch > max) {
                    max = ch;
                    meilleure = c;
                }
            }
            if (meilleure == null) {
                int distance = Integer.MAX_VALUE;
                for (Colonne c : cols) {
                    int d = Math.min(Math.abs(c.fin - v.fin()), Math.abs(c.debut - v.debut()));
                    if (d < distance) {
                        distance = d;
                        meilleure = c;
                    }
                }
            }
            // Position en 1-based dans la ligne d'origine (le caractère de contrôle est retiré).
            return meilleure == null ? "COLONNE " + (v.debut() + 2) : meilleure.nom();
        }

        private String cheminCourant() {
            List<String> l = new ArrayList<>();
            pile.descendingIterator().forEachRemaining(o -> l.add((String) o[1]));
            return String.join(" > ", l);
        }

        private void cloturer(boolean complet) {
            List<String> noms = colonnes == null ? List.of() : colonnes.stream().map(Colonne::nom).toList();
            rapports.add(new RapportVss(code, titre, sreId, sreNom, rollup, entiteFonds, dateTraitement, dateRapport,
                    nbPages, sansDonnees, complet, noms, List.copyOf(lignes)));
            code = null;
        }
    }
}
