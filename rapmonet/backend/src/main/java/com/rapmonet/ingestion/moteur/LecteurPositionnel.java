package com.rapmonet.ingestion.moteur;

import com.rapmonet.parametrage.AttributFormat;
import com.rapmonet.parametrage.FormatFichier;
import com.rapmonet.parametrage.SousFichierFormat;
import com.rapmonet.parametrage.TcFormat;
import com.rapmonet.parametrage.TcrFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Lit un fichier à enregistrements de longueur fixe selon son paramétrage
 * (Fichier → Sous-fichier → TC → TCR → Attributs) et contrôle sa structure.
 *
 * <p>Contrôles bloquants : en-tête et fin de fichier, continuité des séquences,
 * ouverture/fermeture des sous-fichiers, nombre de lignes annoncé en fin de fichier
 * (attribut {@value #NB_ENREGISTREMENTS}).
 * Contrôles non bloquants : longueur de ligne, TC ou TCR inconnu, TC non prévu
 * dans le sous-fichier, valeur d'attribut invalide, nombre de lignes annoncé en fin de
 * sous-fichier (attribut {@value #NB_ENREGISTREMENTS_SOUS_FICHIER}, non fiable dans les LIS du GIM).
 *
 * <p>Les attributs confidentiels (numéros de carte) sont masqués dans les attributs et dans
 * le contenu de la ligne ; une empreinte est ajoutée sous la clé {@code <code>_EMPREINTE}.
 */
public class LecteurPositionnel {

    /** Code de l'attribut qui porte le nombre de lignes dans une fin de fichier ou de sous-fichier. */
    public static final String NB_ENREGISTREMENTS = "NB_ENREGISTREMENTS";

    /** Code de l'attribut qui porte le nombre de lignes dans une fin de sous-fichier. */
    public static final String NB_ENREGISTREMENTS_SOUS_FICHIER = "NB_ENREGISTREMENTS_SOUS_FICHIER";

    /** Suffixe de la clé qui porte l'empreinte d'un attribut confidentiel. */
    public static final String SUFFIXE_EMPREINTE = "_EMPREINTE";

    private final FormatFichier format;
    private final UnaryOperator<String> empreinte;

    /**
     * @param empreinte calcule l'empreinte d'un numéro de carte (ex. HMAC), pour permettre les
     *                  rapprochements sans conserver le numéro en clair
     */
    public LecteurPositionnel(FormatFichier format, UnaryOperator<String> empreinte) {
        this.format = format;
        this.empreinte = empreinte;
    }

    /** Masque un numéro de carte : 6 premiers et 4 derniers caractères visibles. */
    public static String masquer(String valeur) {
        String v = valeur.strip();
        if (v.isEmpty()) {
            return v;
        }
        int debut = v.length() > 10 ? 6 : 0;
        int fin = Math.max(debut, v.length() - 4);
        return v.substring(0, debut) + "*".repeat(fin - debut) + v.substring(fin);
    }

    /** Remplace dans la ligne la valeur de chaque attribut confidentiel par sa forme masquée. */
    public String masquerLigne(String tc, String tcr, String ligne) {
        Optional<TcrFormat> formatTcr = format.tc(tc).flatMap(t -> t.tcr(tcr));
        if (formatTcr.isEmpty()) {
            return ligne;
        }
        StringBuilder resultat = new StringBuilder(ligne);
        for (AttributFormat a : formatTcr.get().attributs()) {
            if (a.confidentiel() && a.positionFin() <= ligne.length()) {
                String brut = ligne.substring(a.position() - 1, a.positionFin());
                String masque = masquer(brut);
                String complete = masque + " ".repeat(a.taille() - masque.length());
                resultat.replace(a.position() - 1, a.positionFin(), complete);
            }
        }
        return resultat.toString();
    }

    public ResultatLecture lire(List<String> lignesBrutes) {
        return new Lecture(retirerLignesVidesFinales(lignesBrutes)).executer();
    }

    /** Décode tous les attributs paramétrés d'une ligne (utilisé pour l'affichage du détail). */
    public Map<String, Object> decoder(String tc, String tcr, String ligne, List<Anomalie> anomalies, int numero) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        Optional<TcrFormat> formatTcr = format.tc(tc).flatMap(t -> t.tcr(tcr));
        if (formatTcr.isEmpty()) {
            return valeurs;
        }
        for (AttributFormat a : formatTcr.get().attributs()) {
            String brut = new com.rapmonet.parametrage.Zone(a.position(), a.taille()).extraire(ligne);
            try {
                Object v = DecodeurAttribut.decoder(a, brut);
                if (v instanceof String texte && a.confidentiel()) {
                    if (!texte.isBlank()) {
                        valeurs.put(a.code() + SUFFIXE_EMPREINTE, empreinte.apply(texte.strip()));
                    }
                    v = masquer(texte);
                }
                if (v != null) {
                    valeurs.put(a.code(), v);
                }
            } catch (ValeurInvalideException e) {
                anomalies.add(Anomalie.avertissement(numero,
                        "TC %s/TCR %s, attribut %s (position %d) : %s".formatted(tc, tcr, a.code(), a.position(), e.getMessage())));
            }
        }
        return valeurs;
    }

    private static List<String> retirerLignesVidesFinales(List<String> lignes) {
        int fin = lignes.size();
        while (fin > 0 && lignes.get(fin - 1).isBlank()) {
            fin--;
        }
        return lignes.subList(0, fin);
    }

    /** État d'une lecture (une instance par fichier lu). */
    private final class Lecture {

        private final List<String> brutes;
        private final List<LigneLue> lignes = new ArrayList<>();
        private final List<TransactionLue> transactions = new ArrayList<>();
        private final List<Anomalie> anomalies = new ArrayList<>();
        private Map<String, Object> entete = Map.of();
        private Map<String, Object> fin = Map.of();

        private SousFichierFormat sousFichierOuvert;
        private int ligneOuvertureSousFichier;
        private List<LigneLue> transactionEnCours;
        private String tcEnCours;
        private boolean finAtteinte;

        Lecture(List<String> brutes) {
            this.brutes = brutes;
        }

        ResultatLecture executer() {
            if (brutes.isEmpty()) {
                anomalies.add(Anomalie.bloquante(null, "Le fichier est vide."));
                return resultat();
            }
            for (int i = 0; i < brutes.size(); i++) {
                traiter(i + 1, brutes.get(i));
            }
            cloturerTransaction();
            if (sousFichierOuvert != null) {
                anomalies.add(Anomalie.bloquante(ligneOuvertureSousFichier,
                        "Le sous-fichier %s n'est pas fermé (TC %s absent)."
                                .formatted(sousFichierOuvert.code(), sousFichierOuvert.tcFin())));
            }
            if (!finAtteinte) {
                anomalies.add(Anomalie.bloquante(null, "Fin de fichier absente (TC %s).".formatted(format.tcFin())));
            } else {
                controlerNombre(fin, NB_ENREGISTREMENTS, Gravite.BLOQUANTE, brutes.size(), brutes.size(), "le fichier");
            }
            return resultat();
        }

        private ResultatLecture resultat() {
            return new ResultatLecture(List.copyOf(lignes), List.copyOf(transactions), entete, fin, List.copyOf(anomalies));
        }

        private void traiter(int numero, String brute) {
            if (finAtteinte) {
                anomalies.add(Anomalie.bloquante(numero, "Ligne présente après la fin de fichier."));
                return;
            }
            if (brute.isBlank()) {
                anomalies.add(Anomalie.bloquante(numero, "Ligne vide."));
                return;
            }
            if (brute.length() != format.longueurEnregistrement()) {
                anomalies.add(Anomalie.avertissement(numero, "Longueur %d au lieu de %d."
                        .formatted(brute.length(), format.longueurEnregistrement())));
            }
            String tc = format.zoneTc().extraire(brute);
            String tcr = format.zoneTcr().extraire(brute);
            String texteSequence = format.zoneSequence().extraire(brute);
            int sequence;
            try {
                sequence = Integer.parseInt(texteSequence.strip());
            } catch (NumberFormatException e) {
                anomalies.add(Anomalie.bloquante(numero, "Numéro de séquence illisible « %s ».".formatted(texteSequence)));
                return;
            }
            if (sequence != numero) {
                anomalies.add(Anomalie.bloquante(numero,
                        "Rupture de séquence : %d attendu, %d trouvé.".formatted(numero, sequence)));
            }

            Optional<TcFormat> formatTc = format.tc(tc);
            if (formatTc.isEmpty()) {
                anomalies.add(Anomalie.avertissement(numero, "TC %s inconnu du paramétrage.".formatted(tc)));
            } else if (formatTc.get().tcr(tcr).isEmpty()) {
                anomalies.add(Anomalie.avertissement(numero, "TCR %s inconnu pour le TC %s.".formatted(tcr, tc)));
            }
            LigneLue ligne = new LigneLue(numero, tc, sequence, tcr, masquerLigne(tc, tcr, brute),
                    decoder(tc, tcr, brute, anomalies, numero));
            lignes.add(ligne);

            if (numero == 1) {
                if (!tc.equals(format.tcEntete())) {
                    anomalies.add(Anomalie.bloquante(1, "En-tête de fichier attendu (TC %s), TC %s trouvé."
                            .formatted(format.tcEntete(), tc)));
                } else {
                    entete = ligne.attributs();
                }
                return;
            }
            if (tc.equals(format.tcFin())) {
                cloturerTransaction();
                finAtteinte = true;
                fin = ligne.attributs();
                return;
            }
            Optional<SousFichierFormat> ouverture = format.sousFichierParEntete(tc);
            if (ouverture.isPresent()) {
                cloturerTransaction();
                if (sousFichierOuvert != null) {
                    anomalies.add(Anomalie.bloquante(numero, "Ouverture du sous-fichier %s alors que %s n'est pas fermé."
                            .formatted(ouverture.get().code(), sousFichierOuvert.code())));
                }
                sousFichierOuvert = ouverture.get();
                ligneOuvertureSousFichier = numero;
                return;
            }
            Optional<SousFichierFormat> fermeture = format.sousFichierParFin(tc);
            if (fermeture.isPresent()) {
                cloturerTransaction();
                if (sousFichierOuvert == null || !sousFichierOuvert.code().equals(fermeture.get().code())) {
                    anomalies.add(Anomalie.bloquante(numero, "Fin du sous-fichier %s sans ouverture correspondante."
                            .formatted(fermeture.get().code())));
                } else {
                    controlerNombre(ligne.attributs(), NB_ENREGISTREMENTS_SOUS_FICHIER, Gravite.AVERTISSEMENT,
                            numero - ligneOuvertureSousFichier + 1, numero, "le sous-fichier " + sousFichierOuvert.code());
                }
                sousFichierOuvert = null;
                return;
            }
            ajouterATransaction(numero, ligne);
        }

        private void ajouterATransaction(int numero, LigneLue ligne) {
            if (sousFichierOuvert == null) {
                anomalies.add(Anomalie.bloquante(numero, "TC %s en dehors de tout sous-fichier.".formatted(ligne.tc())));
                return;
            }
            // Une transaction commence à son premier TCR (TCR 0 pour un TC inconnu du paramétrage).
            String codePremierTcr = format.tc(ligne.tc())
                    .filter(t -> !t.tcrs().isEmpty())
                    .map(t -> t.tcrs().getFirst().code())
                    .orElse("0");
            boolean premierTcr = codePremierTcr.equals(ligne.tcr());
            if (transactionEnCours == null || !ligne.tc().equals(tcEnCours) || premierTcr) {
                cloturerTransaction();
                if (!premierTcr) {
                    anomalies.add(Anomalie.avertissement(numero,
                            "TCR %s du TC %s sans TCR initial.".formatted(ligne.tcr(), ligne.tc())));
                }
                if (!sousFichierOuvert.tcAutorises().isEmpty() && !sousFichierOuvert.tcAutorises().contains(ligne.tc())) {
                    anomalies.add(Anomalie.avertissement(numero,
                            "TC %s non prévu dans le sous-fichier %s.".formatted(ligne.tc(), sousFichierOuvert.code())));
                }
                transactionEnCours = new ArrayList<>();
                tcEnCours = ligne.tc();
            }
            transactionEnCours.add(ligne);
        }

        private void cloturerTransaction() {
            if (transactionEnCours == null) {
                return;
            }
            Map<String, Object> specifiques = new LinkedHashMap<>();
            for (LigneLue l : transactionEnCours) {
                format.tc(l.tc()).flatMap(t -> t.tcr(l.tcr())).ifPresent(f -> f.attributs().stream()
                        .filter(AttributFormat::specifique)
                        .filter(a -> l.attributs().containsKey(a.code()))
                        .forEach(a -> {
                            specifiques.put(a.code(), l.attributs().get(a.code()));
                            String cleEmpreinte = a.code() + SUFFIXE_EMPREINTE;
                            if (l.attributs().containsKey(cleEmpreinte)) {
                                specifiques.put(cleEmpreinte, l.attributs().get(cleEmpreinte));
                            }
                        }));
            }
            transactions.add(new TransactionLue(transactions.size() + 1, sousFichierOuvert.code(), tcEnCours,
                    List.copyOf(transactionEnCours), specifiques));
            transactionEnCours = null;
            tcEnCours = null;
        }

        private void controlerNombre(Map<String, Object> attributs, String code, Gravite gravite,
                                     int attendu, Integer numero, String objet) {
            Object annonce = attributs.get(code);
            if (annonce instanceof Number n && n.longValue() != attendu) {
                anomalies.add(new Anomalie(numero, gravite, "Nombre de lignes annoncé pour %s : %d ; nombre réel : %d."
                        .formatted(objet, n.longValue(), attendu)));
            }
        }
    }
}
