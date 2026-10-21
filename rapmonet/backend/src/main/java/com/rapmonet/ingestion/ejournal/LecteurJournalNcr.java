package com.rapmonet.ingestion.ejournal;

import com.rapmonet.ingestion.moteur.Anomalie;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lit le journal électronique d'un GAB NCR.
 *
 * <p>Le fichier brut utilise le retour chariot seul comme fin de ligne et contient des séquences
 * de commande d'imprimante (ESC […]) : elles sont retirées. Les sessions client vont de
 * « *TRANSACTION START* » à « TRANSACTION END » ; les opérations sont lues sur les tickets
 * (« CASH WITHRAWAL : … » pour un retrait, « nn  BALANCE INQUIRY » pour une demande de solde).
 * Les passages en mode superviseur (arrêté des compteurs, ajout d'espèces) sont lus comme événements.
 */
public class LecteurJournalNcr {

    /** Format par défaut de la date des tickets NCR : « 26/05/22 » = 22 mai 2026. */
    public static final String FORMAT_DATE_DEFAUT = "yy/MM/dd";

    private static final Pattern SEQUENCES_IMPRIMANTE =
            Pattern.compile("\u001b\\[[0-9;]*[A-Za-z]|\u001b[()][0-9A-Za-z]|\u001b[a-zA-Z][0-9]*|[\u000e\u000f]");
    private static final Pattern NUMERO_CARTE = Pattern.compile("\\d{6}[_*Xx\\d]{2,9}\\d{4}");
    private static final Pattern CARTE_SEULE = Pattern.compile("^\\s*(" + NUMERO_CARTE.pattern() + ")\\s*$");
    private static final Pattern NUMERO_EN_CLAIR = Pattern.compile("(?<!\\d)(\\d{6})(\\d{3,9})(\\d{4})(?!\\d)");
    private static final Pattern RETRAIT = Pattern.compile("CASH WITHD?RAWAL\\s*:\\s*(\\S+)(?:\\s+NUM GAB\\s*:\\s*(\\S+))?");
    private static final Pattern DATE_TICKET = Pattern.compile("^\\s*(\\d{2}/\\d{2}/\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2})(?:\\s+(\\d+))?\\s*$");
    private static final Pattern SOLDE = Pattern.compile("^\\s*(\\d{2})\\s+BALANCE INQUIRY");
    private static final Pattern MONTANT = Pattern.compile("^\\s*AMOUNT\\s*:\\s*(\\d+)\\s*([A-Z]{3})?");
    private static final Pattern REPONSE = Pattern.compile("^\\s*RESPONSE\\s*:\\s*(\\d+)");
    private static final Pattern COMPTE = Pattern.compile("^\\s*ACCOUNT\\s*:\\s*(\\S*)");
    private static final Pattern BILLETS_PRESENTES = Pattern.compile("NOTES PRESENTED\\s+([\\d,]+)");
    private static final Pattern HEURE = Pattern.compile("(\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern LIGNE_COMPTEUR = Pattern.compile("^\\s*[+=]?(DENOMINATION|DISPENSED|REJECTED|REMAINING|CASSETTE|TOTAL)\\s+([\\d\\s]+)$");
    private static final Pattern DATE_SUPERVISEUR = Pattern.compile("DATE-TIME\\s*=\\s*(\\d{2}/\\d{2}/\\d{2})\\s+(\\d{1,2}:\\d{2})");
    private static final Pattern HORODATAGE_US = Pattern.compile("^(\\d{1,2}/\\d{1,2}/\\d{4} \\d{1,2}:\\d{2}:\\d{2} [AP]M)$");
    private static final Pattern ESPECES_AJOUTEES = Pattern.compile("TYPE\\s*(\\d)\\s*=\\s*(\\d+)");

    /** Incidents reconnus dans une session : texte du journal → libellé. */
    private static final Map<String, String> INCIDENTS = new LinkedHashMap<>();

    static {
        INCIDENTS.put("CUSTOMER CANCELLED", "Annulée par le client");
        INCIDENTS.put("CANCEL FROM TIMEOUT STATE", "Annulée après délai dépassé");
        INCIDENTS.put("TIMEOUT FROM TIMEOUT STATE", "Délai dépassé");
        INCIDENTS.put("EXTERNAL AUTHENTICATE: NO ARPC", "Pas de cryptogramme de réponse de l'émetteur (ARPC)");
        INCIDENTS.put("GENAC 2 : AAC", "Refusée par la carte (GENAC 2 AAC)");
        INCIDENTS.put("CARD CAPTURED", "Carte capturée");
        INCIDENTS.put("CARD RETAINED", "Carte capturée");
        INCIDENTS.put("NOTES RETRACTED", "Billets retirés (non pris par le client)");
        INCIDENTS.put("RETRACT", "Billets retirés (non pris par le client)");
    }

    private final DateTimeFormatter formatTicket;
    private final DateTimeFormatter formatDate;

    /** @param formatDate format de la date des tickets, ex. « yy/MM/dd » (voir {@link #FORMAT_DATE_DEFAUT}) */
    public LecteurJournalNcr(String formatDate) {
        this.formatDate = DateTimeFormatter.ofPattern(formatDate);
        this.formatTicket = DateTimeFormatter.ofPattern(formatDate + " HH:mm:ss");
    }

    /** Texte brut → lignes nettoyées (séquences d'imprimante retirées, fins de ligne CR, LF ou CRLF). */
    public static List<String> lignes(String brut) {
        String texte = SEQUENCES_IMPRIMANTE.matcher(brut).replaceAll("");
        return Arrays.stream(texte.split("\\r\\n|\\r|\\n", -1)).map(String::stripTrailing).toList();
    }

    /**
     * Masque un numéro de carte qui apparaîtrait en clair (le GAB les masque normalement déjà) :
     * suite de 13 à 19 chiffres à clé de Luhn valide, hors lignes « ACCOUNT » (numéros de compte).
     */
    static String masquerNumerosEnClair(String texte) {
        return Arrays.stream(texte.split("\n", -1))
                .map(l -> l.contains("ACCOUNT") ? l : NUMERO_EN_CLAIR.matcher(l).replaceAll(m -> luhn(m.group())
                        ? m.group(1) + "*".repeat(m.group(2).length()) + m.group(3) : m.group()))
                .collect(Collectors.joining("\n"));
    }

    static boolean luhn(String chiffres) {
        int somme = 0;
        boolean double_ = false;
        for (int i = chiffres.length() - 1; i >= 0; i--) {
            int c = chiffres.charAt(i) - '0';
            if (double_) {
                c *= 2;
                if (c > 9) {
                    c -= 9;
                }
            }
            somme += c;
            double_ = !double_;
        }
        return somme % 10 == 0;
    }

    public ResultatLectureJournal lire(List<String> lignes) {
        List<SessionGab> sessions = new ArrayList<>();
        List<EvenementGab> evenements = new ArrayList<>();
        List<Anomalie> anomalies = new ArrayList<>();

        List<String> session = null;
        int debutSession = 0;
        List<String> superviseur = null;
        int debutSuperviseur = 0;
        for (int i = 0; i < lignes.size(); i++) {
            String l = lignes.get(i);
            int numero = i + 1;
            if (l.contains("*TRANSACTION START*")) {
                if (session != null) {
                    anomalies.add(Anomalie.avertissement(debutSession, "Session interrompue (pas de TRANSACTION END)."));
                    sessions.add(session(sessions.size() + 1, debutSession, session, false, anomalies));
                }
                session = new ArrayList<>();
                debutSession = numero;
                continue;
            }
            if (session != null) {
                if (l.contains("TRANSACTION END")) {
                    session.add(l);
                    sessions.add(session(sessions.size() + 1, debutSession, session, true, anomalies));
                    session = null;
                } else if (!l.contains("INVALID FORMAT")) {
                    session.add(l);
                }
                continue;
            }
            if (l.contains("SUPERVISOR MODE ENTRY")) {
                superviseur = new ArrayList<>();
                debutSuperviseur = numero;
            }
            if (superviseur != null && !l.contains("INVALID FORMAT")) {
                superviseur.add(l);
                if (l.contains("SUPERVISOR MODE EXIT")) {
                    evenement(debutSuperviseur, superviseur).ifPresent(evenements::add);
                    superviseur = null;
                }
            }
        }
        if (session != null) {
            anomalies.add(Anomalie.avertissement(debutSession, "Dernière session incomplète (fichier coupé ?)."));
            sessions.add(session(sessions.size() + 1, debutSession, session, false, anomalies));
        }
        if (superviseur != null) {
            evenement(debutSuperviseur, superviseur).ifPresent(evenements::add);
        }
        if (sessions.isEmpty() && evenements.isEmpty()) {
            anomalies.add(Anomalie.bloquante(null, "Aucune session client (TRANSACTION START) ni événement superviseur : "
                    + "ce fichier n'est pas un journal électronique NCR."));
        }
        String terminal = sessions.stream().flatMap(s -> s.operations().stream()).map(OperationGab::terminal)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.groupingBy(t -> t, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return new ResultatLectureJournal(terminal, List.copyOf(sessions), List.copyOf(evenements), List.copyOf(anomalies));
    }

    private SessionGab session(int rang, int numeroDebut, List<String> lignes, boolean complete, List<Anomalie> anomalies) {
        List<OperationGab> operations = new ArrayList<>();
        Set<String> incidents = new LinkedHashSet<>();
        String carte = null;
        String derniereCarte = null;
        LocalTime heureDebut = null;
        String[] dateEnAttente = null;
        int ligneDateEnAttente = 0;
        Brouillon courant = null;
        List<Integer> billets = null;
        boolean billetsPris = false;
        Map<String, List<Integer>> compteurs = new LinkedHashMap<>();

        for (int i = 0; i < lignes.size(); i++) {
            String l = lignes.get(i);
            int numero = numeroDebut + i + 1;
            if (heureDebut == null) {
                Matcher h = HEURE.matcher(l);
                if (h.find()) {
                    heureDebut = LocalTime.parse(h.group(1));
                }
            }
            Matcher m;
            if ((m = CARTE_SEULE.matcher(l)).matches()) {
                derniereCarte = masquerNumerosEnClair(m.group(1));
                if (carte == null) {
                    carte = derniereCarte;
                }
            } else if ((m = RETRAIT.matcher(l)).find()) {
                courant = termine(courant, operations, anomalies);
                courant = new Brouillon(TypeOperationGab.RETRAIT, masquerNumerosEnClair(m.group(1)), m.group(2), numero);
                if (carte == null) {
                    carte = courant.carte;
                }
            } else if ((m = DATE_TICKET.matcher(l)).matches()) {
                if (courant != null && courant.type == TypeOperationGab.RETRAIT && courant.date == null) {
                    courant.date = new String[]{m.group(1), m.group(2)};
                    courant.reference = m.group(3);
                } else {
                    dateEnAttente = new String[]{m.group(1), m.group(2), m.group(3)};
                    ligneDateEnAttente = numero;
                }
            } else if ((m = SOLDE.matcher(l)).find()) {
                courant = termine(courant, operations, anomalies);
                courant = new Brouillon(TypeOperationGab.SOLDE, derniereCarte, null, ligneDateEnAttente > 0 ? ligneDateEnAttente : numero);
                courant.codeReponse = m.group(1);
                if (dateEnAttente != null) {
                    courant.date = new String[]{dateEnAttente[0], dateEnAttente[1]};
                    courant.reference = dateEnAttente[2];
                    dateEnAttente = null;
                }
            } else if (courant != null && (m = MONTANT.matcher(l)).find()) {
                courant.montant = Long.parseLong(m.group(1));
                courant.devise = m.group(2);
            } else if (courant != null && (m = REPONSE.matcher(l)).find()) {
                courant.codeReponse = m.group(1);
            } else if (courant != null && (m = COMPTE.matcher(l)).find() && courant.compte == null) {
                courant.compte = m.group(1).isBlank() ? null : m.group(1);
            } else if ((m = BILLETS_PRESENTES.matcher(l)).find()) {
                billets = Arrays.stream(m.group(1).split(",")).filter(s -> !s.isBlank()).map(Integer::parseInt).toList();
            } else if (l.contains("NOTES TAKEN")) {
                billetsPris = true;
            } else if ((m = LIGNE_COMPTEUR.matcher(l)).matches()) {
                compteurs.put(m.group(1), entiers(m.group(2)));
            }
            for (Map.Entry<String, String> incident : INCIDENTS.entrySet()) {
                if (l.contains(incident.getKey())) {
                    incidents.add(incident.getValue());
                }
            }
        }
        termine(courant, operations, anomalies);
        CompteursCassettes cc = compteurs.containsKey("DISPENSED")
                ? new CompteursCassettes(compteurs.get("DENOMINATION"), compteurs.get("DISPENSED"),
                compteurs.get("REJECTED"), compteurs.get("REMAINING"))
                : null;
        if (billets != null && !billetsPris && complete) {
            incidents.add("Billets présentés mais non pris");
        }
        String texte = masquerNumerosEnClair(String.join("\n", lignes));
        return new SessionGab(rang, numeroDebut, heureDebut, carte, List.copyOf(operations), billets, billetsPris, cc,
                List.copyOf(incidents), texte, complete);
    }

    /** Opération en cours de lecture. */
    private final class Brouillon {
        final TypeOperationGab type;
        final String carte;
        final String terminal;
        final int numeroLigne;
        String[] date;
        String reference;
        Long montant;
        String devise;
        String codeReponse;
        String compte;

        Brouillon(TypeOperationGab type, String carte, String terminal, int numeroLigne) {
            this.type = type;
            this.carte = carte;
            this.terminal = terminal;
            this.numeroLigne = numeroLigne;
        }
    }

    private Brouillon termine(Brouillon b, List<OperationGab> operations, List<Anomalie> anomalies) {
        if (b == null) {
            return null;
        }
        LocalDateTime dateHeure = null;
        if (b.date != null) {
            try {
                dateHeure = LocalDateTime.parse(b.date[0] + " " + b.date[1], formatTicket);
            } catch (DateTimeParseException e) {
                anomalies.add(Anomalie.avertissement(b.numeroLigne, "Date de ticket illisible « %s » (format attendu : %s)."
                        .formatted(b.date[0], formatDate.toString())));
            }
        } else {
            anomalies.add(Anomalie.avertissement(b.numeroLigne, "Ticket sans date ni heure."));
        }
        operations.add(new OperationGab(b.type, b.carte, b.terminal, dateHeure, b.reference, b.montant, b.devise,
                b.codeReponse, b.compte, b.numeroLigne));
        return null;
    }

    private java.util.Optional<EvenementGab> evenement(int numero, List<String> lignes) {
        List<String> actions = new ArrayList<>();
        Map<String, List<Integer>> compteurs = new LinkedHashMap<>();
        Map<String, Integer> especes = new LinkedHashMap<>();
        LocalDateTime dateHeure = null;
        boolean dansAjout = false;
        for (String l : lignes) {
            Matcher m;
            if (dateHeure == null && (m = DATE_SUPERVISEUR.matcher(l)).find()) {
                try {
                    LocalDate d = LocalDate.parse(m.group(1), formatDate);
                    dateHeure = d.atTime(LocalTime.parse(m.group(2).length() == 4 ? "0" + m.group(2) : m.group(2)));
                } catch (DateTimeParseException e) {
                    // date laissée vide
                }
            }
            if (dateHeure == null && (m = HORODATAGE_US.matcher(l.strip())).matches()) {
                dateHeure = LocalDateTime.parse(m.group(1), DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.ENGLISH));
            }
            if (l.contains("CASH COUNTS CLEARED")) {
                actions.add("REMISE_A_ZERO_COMPTEURS");
            }
            if (l.contains("CARDS CLEARED")) {
                actions.add("RETRAIT_CARTES_CAPTUREES");
            }
            if (l.contains("CASH ADDED")) {
                actions.add("AJOUT_ESPECES");
                dansAjout = true;
                continue;
            }
            if (dansAjout) {
                Matcher t = ESPECES_AJOUTEES.matcher(l);
                boolean trouve = false;
                while (t.find()) {
                    especes.put("TYPE" + t.group(1), Integer.parseInt(t.group(2)));
                    trouve = true;
                }
                dansAjout = trouve;
            }
            if ((m = LIGNE_COMPTEUR.matcher(l)).matches() && !l.contains("DENOMINATION")) {
                compteurs.computeIfAbsent(m.group(1), k -> new ArrayList<>()).addAll(entiers(m.group(2)));
            }
        }
        boolean utile = !actions.isEmpty() || !compteurs.isEmpty();
        return utile
                ? java.util.Optional.of(new EvenementGab(numero, dateHeure, List.copyOf(new LinkedHashSet<>(actions)),
                releves(compteurs), especes, String.join("\n", lignes)))
                : java.util.Optional.empty();
    }

    /** Les compteurs imprimés plusieurs fois (4 types par relevé) sont découpés en relevés successifs. */
    private static List<Map<String, List<Integer>>> releves(Map<String, List<Integer>> compteurs) {
        int nbReleves = compteurs.values().stream().mapToInt(l -> (l.size() + 3) / 4).max().orElse(0);
        List<Map<String, List<Integer>>> releves = new ArrayList<>();
        for (int r = 0; r < nbReleves; r++) {
            Map<String, List<Integer>> releve = new LinkedHashMap<>();
            for (Map.Entry<String, List<Integer>> e : compteurs.entrySet()) {
                List<Integer> v = e.getValue();
                if (v.size() > r * 4) {
                    releve.put(e.getKey(), List.copyOf(v.subList(r * 4, Math.min(v.size(), r * 4 + 4))));
                }
            }
            releves.add(releve);
        }
        return releves;
    }

    private static List<Integer> entiers(String texte) {
        return Arrays.stream(texte.strip().split("\\s+")).filter(s -> !s.isBlank()).map(Integer::parseInt).toList();
    }
}
