package com.rapmonet.compense.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Référentiel des commerçants (terminal, compte à créditer, taux par réseau) et comptes généraux de la compense. */
@Service
@Transactional
public class CommercantsService {

    public record Commercant(String terminalId, String nom, String compte, BigDecimal tauxGim, BigDecimal tauxVisa,
                             BigDecimal tauxMci, boolean actif) {

        BigDecimal taux(String reseau) {
            return switch (reseau) {
                case "VISA" -> tauxVisa;
                case "MASTERCARD" -> tauxMci;
                default -> tauxGim;
            };
        }
    }

    /** @param lignes lignes de commerçants lues ; @param anomalies lignes ignorées et motif */
    public record RapportImport(int lignes, int crees, int modifies, List<String> anomalies) {
    }

    /** Comptes généraux et paramètres de calcul de la compense (parametre_application, préfixe compense.). */
    public record Parametres(String compteEmisOnus, String compteAcqGim, String compteAcqVisa, String compteAcqMci,
                             String compteCompensationVisaMci, String compteCompensationGim, String compteCommission,
                             String compteTaf, String compteCommissionGim, BigDecimal tauxTaf, String codeOperation,
                             String devise) {
    }

    static final Map<String, String> CLES = new LinkedHashMap<>();

    static {
        CLES.put("compteEmisOnus", "compense.compte.emis_onus");
        CLES.put("compteAcqGim", "compense.compte.acq_gim");
        CLES.put("compteAcqVisa", "compense.compte.acq_visa");
        CLES.put("compteAcqMci", "compense.compte.acq_mci");
        CLES.put("compteCompensationVisaMci", "compense.compte.compensation_visamci");
        CLES.put("compteCompensationGim", "compense.compte.compensation_gim");
        CLES.put("compteCommission", "compense.compte.commission");
        CLES.put("compteTaf", "compense.compte.taf");
        CLES.put("compteCommissionGim", "compense.compte.commission_gim");
    }

    private static final Pattern COMPTE = Pattern.compile("\\d{6,30}");

    private final JdbcClient jdbc;
    private final JournalAudit audit;

    CommercantsService(JdbcClient jdbc, JournalAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ commerçants

    @Transactional(readOnly = true)
    public List<Commercant> commercants() {
        return jdbc.sql("select * from commercant order by nom, terminal_id").query((rs, n) -> new Commercant(
                rs.getString("terminal_id"), rs.getString("nom"), rs.getString("compte"), rs.getBigDecimal("taux_gim"),
                rs.getBigDecimal("taux_visa"), rs.getBigDecimal("taux_mci"), rs.getBoolean("actif"))).list();
    }

    @Transactional(readOnly = true)
    public Map<String, Commercant> parTerminal() {
        Map<String, Commercant> m = new LinkedHashMap<>();
        commercants().forEach(c -> m.put(c.terminalId(), c));
        return m;
    }

    public Commercant enregistrer(Commercant c, String utilisateur) {
        Commercant v = controler(c);
        ecrire(v, utilisateur);
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_COMPENSE, "Commerçant " + v.terminalId(),
                "%s, compte %s, taux GIM %s / VISA %s / MCI %s%s".formatted(v.nom(), v.compte(), v.tauxGim(), v.tauxVisa(),
                        v.tauxMci(), v.actif() ? "" : ", désactivé"), true);
        return v;
    }

    public void supprimer(String terminal, String utilisateur) {
        jdbc.sql("delete from commercant where terminal_id = ?").param(terminal).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_COMPENSE, "Commerçant " + terminal, "Suppression", true);
    }

    private Commercant controler(Commercant c) {
        String terminal = normaliserTerminal(c.terminalId());
        if (terminal == null) {
            throw new RegleMetierException("L'identifiant du terminal est obligatoire.");
        }
        if (c.nom() == null || c.nom().isBlank()) {
            throw new RegleMetierException("Le nom du commerçant est obligatoire (terminal " + terminal + ").");
        }
        String compte = c.compte() == null ? "" : c.compte().strip();
        if (!COMPTE.matcher(compte).matches()) {
            throw new RegleMetierException("Compte du commerçant invalide pour le terminal " + terminal + " : « " + compte + " ».");
        }
        return new Commercant(terminal, c.nom().strip(), compte, taux(c.tauxGim()), taux(c.tauxVisa()), taux(c.tauxMci()),
                c.actif());
    }

    private void ecrire(Commercant c, String utilisateur) {
        jdbc.sql("""
                        insert into commercant (terminal_id, nom, compte, taux_gim, taux_visa, taux_mci, actif, modifie_par)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (terminal_id) do update set nom = excluded.nom, compte = excluded.compte,
                            taux_gim = excluded.taux_gim, taux_visa = excluded.taux_visa, taux_mci = excluded.taux_mci,
                            actif = excluded.actif, modifie_par = excluded.modifie_par, modifie_le = now()""")
                .params(c.terminalId(), c.nom(), c.compte(), c.tauxGim(), c.tauxVisa(), c.tauxMci(), c.actif(), utilisateur)
                .update();
    }

    /** Taux en fraction (0,015) ; une valeur supérieure à 1 est lue comme un pourcentage (1,5 → 0,015), comme la macro. */
    static BigDecimal taux(BigDecimal t) {
        if (t == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = t.compareTo(BigDecimal.ONE) > 0 ? t.divide(BigDecimal.valueOf(100), 7, RoundingMode.HALF_UP) : t;
        if (v.signum() < 0 || v.compareTo(new BigDecimal("0.2")) > 0) {
            throw new RegleMetierException("Taux invalide : " + t + " (attendu entre 0 et 20 %).");
        }
        return v.setScale(5, RoundingMode.HALF_UP);
    }

    /** Terminal sur 8 chiffres (zéros à gauche), comme la macro ; un identifiant non numérique est gardé tel quel. */
    static String normaliserTerminal(String t) {
        if (t == null || t.isBlank()) {
            return null;
        }
        String s = t.strip();
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        if (s.matches("\\d+") && s.length() < 8) {
            s = "0".repeat(8 - s.length()) + s;
        }
        return s;
    }

    // ------------------------------------------------------------------ import Excel

    /**
     * Importe la feuille des comptes commerçants de la macro (nom contenant « COMPTES », en-tête « TERMINAL ID ») :
     * colonnes terminal, nom, compte, taux GIM, taux VISA, taux MCI.
     */
    public RapportImport importer(byte[] contenu, String nomFichier, String utilisateur) {
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet feuille = feuilleDesComptes(classeur).orElseThrow(() -> new RegleMetierException(
                    "Feuille des comptes commerçants introuvable : une feuille doit avoir « TERMINAL ID » en colonne A."));
            int entete = ligneEntete(feuille);
            DataFormatter fmt = new DataFormatter();
            int lignes = 0;
            int crees = 0;
            int modifies = 0;
            List<String> anomalies = new ArrayList<>();
            Map<String, Commercant> existants = parTerminal();
            for (int r = entete + 1; r <= feuille.getLastRowNum(); r++) {
                Row row = feuille.getRow(r);
                if (row == null) {
                    continue;
                }
                String terminal = texte(row.getCell(0), fmt);
                if (terminal.isBlank() || terminal.equals("0")) {
                    continue;
                }
                lignes++;
                try {
                    Commercant c = controler(new Commercant(terminal, texte(row.getCell(1), fmt), texte(row.getCell(2), fmt),
                            nombre(row.getCell(3), fmt), nombre(row.getCell(4), fmt), nombre(row.getCell(5), fmt), true));
                    Commercant avant = existants.get(c.terminalId());
                    if (avant == null) {
                        crees++;
                    } else if (!memes(avant, c)) {
                        modifies++;
                    }
                    ecrire(c, utilisateur);
                    existants.put(c.terminalId(), c);
                } catch (RegleMetierException e) {
                    anomalies.add("Ligne " + (r + 1) + " : " + e.getMessage());
                }
            }
            if (lignes == 0) {
                throw new RegleMetierException("Aucun commerçant dans la feuille « " + feuille.getSheetName() + " ».");
            }
            audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_COMPENSE, "Import commerçants " + nomFichier,
                    "%d ligne(s) : %d créé(s), %d modifié(s), %d ignorée(s)".formatted(lignes, crees, modifies, anomalies.size()),
                    true);
            return new RapportImport(lignes, crees, modifies, anomalies);
        } catch (IOException | RuntimeException e) {
            if (e instanceof RegleMetierException r) {
                throw r;
            }
            throw new RegleMetierException("Fichier Excel illisible : " + e.getMessage());
        }
    }

    private static boolean memes(Commercant a, Commercant b) {
        return a.nom().equals(b.nom()) && a.compte().equals(b.compte()) && a.tauxGim().compareTo(b.tauxGim()) == 0
                && a.tauxVisa().compareTo(b.tauxVisa()) == 0 && a.tauxMci().compareTo(b.tauxMci()) == 0 && a.actif() == b.actif();
    }

    private static Optional<Sheet> feuilleDesComptes(Workbook classeur) {
        Sheet trouvee = null;
        for (Sheet s : classeur) {
            if (ligneEntete(s) >= 0) {
                if (s.getSheetName().toUpperCase().contains("COMPTE")) {
                    return Optional.of(s);
                }
                trouvee = trouvee == null ? s : trouvee;
            }
        }
        return Optional.ofNullable(trouvee);
    }

    private static int ligneEntete(Sheet s) {
        DataFormatter fmt = new DataFormatter();
        for (int r = 0; r <= Math.min(20, s.getLastRowNum()); r++) {
            Row row = s.getRow(r);
            if (row != null && texte(row.getCell(0), fmt).equalsIgnoreCase("TERMINAL ID")) {
                return r;
            }
        }
        return -1;
    }

    private static String texte(Cell c, DataFormatter fmt) {
        if (c == null) {
            return "";
        }
        if (c.getCellType() == CellType.NUMERIC) {
            double d = c.getNumericCellValue();
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
        }
        return fmt.formatCellValue(c).strip();
    }

    private static BigDecimal nombre(Cell c, DataFormatter fmt) {
        if (c == null) {
            return BigDecimal.ZERO;
        }
        if (c.getCellType() == CellType.NUMERIC || (c.getCellType() == CellType.FORMULA
                && c.getCachedFormulaResultType() == CellType.NUMERIC)) {
            return BigDecimal.valueOf(c.getNumericCellValue());
        }
        String s = fmt.formatCellValue(c).replace("%", "").replace(',', '.').strip();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal v = new BigDecimal(s);
            // « 1,5 % » saisi en texte : pourcentage.
            return fmt.formatCellValue(c).contains("%") ? v.divide(BigDecimal.valueOf(100), 7, RoundingMode.HALF_UP) : v;
        } catch (NumberFormatException e) {
            throw new RegleMetierException("Taux illisible : « " + s + " ».");
        }
    }

    // ------------------------------------------------------------------ paramètres

    @Transactional(readOnly = true)
    public Parametres parametres() {
        Map<String, String> v = new LinkedHashMap<>();
        jdbc.sql("select cle, valeur from parametre_application where cle like 'compense.%'")
                .query((rs, n) -> v.put(rs.getString(1), rs.getString(2))).list();
        BigDecimal taf;
        try {
            taf = new BigDecimal(v.getOrDefault("compense.taux_taf", "0.17").strip());
        } catch (NumberFormatException e) {
            taf = new BigDecimal("0.17");
        }
        return new Parametres(v.get(CLES.get("compteEmisOnus")), v.get(CLES.get("compteAcqGim")),
                v.get(CLES.get("compteAcqVisa")), v.get(CLES.get("compteAcqMci")),
                v.get(CLES.get("compteCompensationVisaMci")), v.get(CLES.get("compteCompensationGim")),
                v.get(CLES.get("compteCommission")), v.get(CLES.get("compteTaf")), v.get(CLES.get("compteCommissionGim")),
                taf, v.getOrDefault("compense.code_operation", "91000"), v.getOrDefault("compense.devise", "XOF"));
    }

    public Parametres modifierParametres(Parametres p, String utilisateur) {
        Map<String, String> comptes = new LinkedHashMap<>();
        comptes.put("compteEmisOnus", p.compteEmisOnus());
        comptes.put("compteAcqGim", p.compteAcqGim());
        comptes.put("compteAcqVisa", p.compteAcqVisa());
        comptes.put("compteAcqMci", p.compteAcqMci());
        comptes.put("compteCompensationVisaMci", p.compteCompensationVisaMci());
        comptes.put("compteCompensationGim", p.compteCompensationGim());
        comptes.put("compteCommission", p.compteCommission());
        comptes.put("compteTaf", p.compteTaf());
        comptes.put("compteCommissionGim", p.compteCommissionGim());
        comptes.forEach((nom, valeur) -> {
            String v = valeur == null ? "" : valeur.strip();
            if (!v.isEmpty() && !COMPTE.matcher(v).matches()) {
                throw new RegleMetierException("Numéro de compte invalide : « " + v + " » (chiffres uniquement).");
            }
            jdbc.sql("update parametre_application set valeur = ?, modifie_le = now() where cle = ?").params(v, CLES.get(nom)).update();
        });
        if (p.tauxTaf() == null || p.tauxTaf().signum() < 0 || p.tauxTaf().compareTo(BigDecimal.ONE) >= 0) {
            throw new RegleMetierException("Taux de TAF invalide (fraction, ex. 0.17).");
        }
        jdbc.sql("update parametre_application set valeur = ?, modifie_le = now() where cle = 'compense.taux_taf'")
                .param(p.tauxTaf().stripTrailingZeros().toPlainString()).update();
        if (p.codeOperation() != null && !p.codeOperation().isBlank()) {
            jdbc.sql("update parametre_application set valeur = ?, modifie_le = now() where cle = 'compense.code_operation'")
                    .param(p.codeOperation().strip()).update();
        }
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_COMPENSE, "Comptes de la compense TPE",
                "Comptes généraux, TAF " + p.tauxTaf(), true);
        return parametres();
    }

    /** Comptes généraux non renseignés (libellés), qui bloquent la création d'un traitement. */
    static List<String> comptesManquants(Parametres p) {
        List<String> l = new ArrayList<>();
        if (vide(p.compteEmisOnus())) l.add("EMIS ONUS");
        if (vide(p.compteAcqGim())) l.add("ACQ GIM");
        if (vide(p.compteAcqVisa())) l.add("ACQ VISA");
        if (vide(p.compteAcqMci())) l.add("ACQ MCI");
        if (vide(p.compteCompensationVisaMci())) l.add("CMPNSE VISAMCI");
        if (vide(p.compteCompensationGim())) l.add("CMPNSE GIM");
        if (vide(p.compteCommission())) l.add("CPTE COMM");
        if (vide(p.compteTaf())) l.add("CPTE TAF");
        return l;
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
