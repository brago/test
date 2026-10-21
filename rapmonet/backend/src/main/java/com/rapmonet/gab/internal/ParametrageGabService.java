package com.rapmonet.gab.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.gab.BinsBanque;
import com.rapmonet.gab.ReferentielGab;
import com.rapmonet.ingestion.CoupuresGab;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Paramétrage des GAB, de leurs profils (cassettes et coupures) et des BIN cartes. */
@Service
@Transactional
public class ParametrageGabService implements CoupuresGab, BinsBanque, ReferentielGab {

    /** Cassette d'un profil : lettre A à D, valeur du billet, type de billet NCR (1 à 4). */
    public record Cassette(String cassette, long coupure, int type) {
    }

    public record Profil(String code, String libelle, List<Cassette> cassettes) {
    }

    public record Gab(String terminalId, String libelle, String localisation, String agence, String marque,
                      String profilCode, String compteCaisse, String compteManquant, String compteExcedent,
                      boolean actif) {
    }

    public record Bin(String bin, String libelle, String typeCarte) {
    }

    private static final Pattern CASSETTE = Pattern.compile("[A-D]");
    private static final Pattern NUMERIQUE = Pattern.compile("\\d{6,11}");
    private static final Set<String> TYPES_CARTE = Set.of("DEBIT", "CREDIT", "PREPAYEE");

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final JournalAudit audit;

    ParametrageGabService(JdbcClient jdbc, JsonMapper json, JournalAudit audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ profils

    @Transactional(readOnly = true)
    public List<Profil> profils() {
        return jdbc.sql("select * from gab_profil order by code").query((rs, n) -> profil(rs)).list();
    }

    @Transactional(readOnly = true)
    public Profil profil(String code) {
        return jdbc.sql("select * from gab_profil where code = ?").param(code).query((rs, n) -> profil(rs)).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("Profil GAB " + code + " introuvable."));
    }

    private Profil profil(ResultSet rs) throws SQLException {
        return new Profil(rs.getString("code"), rs.getString("libelle"),
                json.readValue(rs.getString("cassettes"), new TypeReference<List<Cassette>>() { }));
    }

    public Profil enregistrerProfil(Profil p, String utilisateur) {
        String code = obligatoire(p.code(), "Le code du profil").toUpperCase();
        String libelle = obligatoire(p.libelle(), "Le libellé du profil");
        List<Cassette> cassettes = controlerCassettes(p.cassettes());
        int n = jdbc.sql("""
                        insert into gab_profil (code, libelle, cassettes, modifie_par) values (?, ?, ?::jsonb, ?)
                        on conflict (code) do update set libelle = excluded.libelle, cassettes = excluded.cassettes,
                            modifie_par = excluded.modifie_par, modifie_le = now()""")
                .params(code, libelle, json.writeValueAsString(cassettes), utilisateur).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "Profil GAB " + code,
                libelle + " : " + cassettes.stream().map(c -> c.cassette() + "=" + c.coupure() + " (type " + c.type() + ")")
                        .reduce((a, b) -> a + ", " + b).orElse(""), n > 0);
        return profil(code);
    }

    /** Cassettes A à D distinctes, coupure positive, type 1 à 4 ; un même type a toujours la même coupure. */
    static List<Cassette> controlerCassettes(List<Cassette> cassettes) {
        if (cassettes == null || cassettes.isEmpty()) {
            throw new RegleMetierException("Le profil doit comporter au moins une cassette.");
        }
        Set<String> vues = new HashSet<>();
        Map<Integer, Long> coupureParType = new HashMap<>();
        for (Cassette c : cassettes) {
            if (c.cassette() == null || !CASSETTE.matcher(c.cassette()).matches() || !vues.add(c.cassette())) {
                throw new RegleMetierException("Cassettes attendues : A, B, C, D (chacune une seule fois).");
            }
            if (c.coupure() <= 0) {
                throw new RegleMetierException("Cassette " + c.cassette() + " : la coupure doit être positive.");
            }
            if (c.type() < 1 || c.type() > 4) {
                throw new RegleMetierException("Cassette " + c.cassette() + " : le type de billet NCR va de 1 à 4.");
            }
            Long autre = coupureParType.putIfAbsent(c.type(), c.coupure());
            if (autre != null && autre != c.coupure()) {
                throw new RegleMetierException("Le type " + c.type() + " ne peut pas avoir deux coupures (" + autre
                        + " et " + c.coupure() + ") : le journal compte les billets par type.");
            }
        }
        return cassettes.stream().sorted((a, b) -> a.cassette().compareTo(b.cassette())).toList();
    }

    public void supprimerProfil(String code, String utilisateur) {
        if (jdbc.sql("select count(*) from gab where profil_code = ?").param(code).query(Integer.class).single() > 0) {
            throw new RegleMetierException("Ce profil est utilisé par au moins un GAB.");
        }
        jdbc.sql("delete from gab_profil where code = ?").param(code).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "Profil GAB " + code, "Suppression", true);
    }

    /** Valeur des billets de type 1 à 4 selon les cassettes du profil (0 = type absent). */
    static List<Long> coupuresParType(List<Cassette> cassettes) {
        List<Long> l = new ArrayList<>(List.of(0L, 0L, 0L, 0L));
        cassettes.forEach(c -> l.set(c.type() - 1, c.coupure()));
        return List.copyOf(l);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<Long>> coupuresParType(String terminal) {
        return jdbc.sql("select p.cassettes from gab g join gab_profil p on p.code = g.profil_code where g.terminal_id = ?")
                .param(terminal).query(String.class).optional()
                .map(c -> coupuresParType(json.readValue(c, new TypeReference<List<Cassette>>() { })));
    }

    // ------------------------------------------------------------------ GAB

    @Transactional(readOnly = true)
    public List<Gab> gabs() {
        return jdbc.sql("select * from gab order by terminal_id").query((rs, n) -> gab(rs)).list();
    }

    @Transactional(readOnly = true)
    public Gab gab(String terminal) {
        return jdbc.sql("select * from gab where terminal_id = ?").param(terminal).query((rs, n) -> gab(rs)).optional()
                .orElseThrow(() -> new RessourceIntrouvableException("GAB " + terminal + " non paramétré."));
    }

    private static Gab gab(ResultSet rs) throws SQLException {
        return new Gab(rs.getString("terminal_id"), rs.getString("libelle"), rs.getString("localisation"),
                rs.getString("agence"), rs.getString("marque"), rs.getString("profil_code"), rs.getString("compte_caisse"),
                rs.getString("compte_manquant"), rs.getString("compte_excedent"), rs.getBoolean("actif"));
    }

    public Gab enregistrerGab(Gab g, String utilisateur) {
        String terminal = obligatoire(g.terminalId(), "L'identifiant du terminal");
        if (!terminal.matches("[A-Za-z0-9_-]{1,20}")) {
            throw new RegleMetierException("Identifiant du terminal : lettres, chiffres, « - » ou « _ » (20 au plus).");
        }
        profil(obligatoire(g.profilCode(), "Le profil GAB"));
        jdbc.sql("""
                        insert into gab (terminal_id, libelle, localisation, agence, marque, profil_code, compte_caisse,
                            compte_manquant, compte_excedent, actif, modifie_par)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (terminal_id) do update set libelle = excluded.libelle,
                            localisation = excluded.localisation, agence = excluded.agence, marque = excluded.marque,
                            profil_code = excluded.profil_code, compte_caisse = excluded.compte_caisse,
                            compte_manquant = excluded.compte_manquant, compte_excedent = excluded.compte_excedent,
                            actif = excluded.actif, modifie_par = excluded.modifie_par, modifie_le = now()""")
                .params(terminal, obligatoire(g.libelle(), "Le libellé"), vide(g.localisation()), vide(g.agence()),
                        obligatoire(g.marque(), "La marque").toUpperCase(), g.profilCode(), vide(g.compteCaisse()),
                        vide(g.compteManquant()), vide(g.compteExcedent()), g.actif(), utilisateur)
                .update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "GAB " + terminal,
                "%s, %s, profil %s%s".formatted(g.libelle(), g.marque(), g.profilCode(), g.actif() ? "" : ", désactivé"), true);
        return gab(terminal);
    }

    public void supprimerGab(String terminal, String utilisateur) {
        try {
            jdbc.sql("delete from gab where terminal_id = ?").param(terminal).update();
        } catch (DataIntegrityViolationException e) {
            throw new RegleMetierException("Ce GAB a des approvisionnements ou des arrêtés : désactivez-le plutôt.");
        }
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "GAB " + terminal, "Suppression", true);
    }

    // ------------------------------------------------------------------ BIN

    @Transactional(readOnly = true)
    public List<Bin> bins() {
        return jdbc.sql("select * from bin_carte order by bin")
                .query((rs, n) -> new Bin(rs.getString("bin"), rs.getString("libelle"), rs.getString("type_carte"))).list();
    }

    public Bin enregistrerBin(Bin b, String utilisateur) {
        String bin = obligatoire(b.bin(), "Le BIN");
        if (!NUMERIQUE.matcher(bin).matches()) {
            throw new RegleMetierException("Le BIN comporte de 6 à 11 chiffres.");
        }
        if (b.typeCarte() == null || !TYPES_CARTE.contains(b.typeCarte())) {
            throw new RegleMetierException("Type de carte attendu : DEBIT, CREDIT ou PREPAYEE.");
        }
        jdbc.sql("""
                        insert into bin_carte (bin, libelle, type_carte, modifie_par) values (?, ?, ?, ?)
                        on conflict (bin) do update set libelle = excluded.libelle, type_carte = excluded.type_carte,
                            modifie_par = excluded.modifie_par, modifie_le = now()""")
                .params(bin, obligatoire(b.libelle(), "Le libellé du produit"), b.typeCarte(), utilisateur).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "BIN " + bin, b.libelle() + " (" + b.typeCarte() + ")", true);
        return new Bin(bin, b.libelle().strip(), b.typeCarte());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean carteDeLaBanque(String carte) {
        if (carte == null || carte.length() < 6) {
            return false;
        }
        // Carte masquée (6 premiers + 4 derniers chiffres) : seuls les 6 premiers chiffres du BIN sont comparables.
        String c = carte.strip();
        boolean masquee = c.length() > 6 && !Character.isDigit(c.charAt(6));
        String sql = masquee
                ? "select count(*) from bin_carte where left(bin, 6) = left(?, 6)"
                : "select count(*) from bin_carte where ? like bin || '%'";
        return jdbc.sql(sql).param(c).query(Integer.class).single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean binsParametres() {
        return jdbc.sql("select count(*) from bin_carte").query(Integer.class).single() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> libellesGab() {
        Map<String, String> m = new HashMap<>();
        jdbc.sql("select terminal_id, libelle from gab").query((rs, n) -> m.put(rs.getString(1), rs.getString(2))).list();
        return m;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> produitsBin() {
        Map<String, String> m = new HashMap<>();
        jdbc.sql("select bin, libelle from bin_carte").query((rs, n) -> m.put(rs.getString(1), rs.getString(2))).list();
        return m;
    }

    public void supprimerBin(String bin, String utilisateur) {
        jdbc.sql("delete from bin_carte where bin = ?").param(bin).update();
        audit.enregistrerPour(utilisateur, ActionsAudit.PARAMETRAGE_GAB, "BIN " + bin, "Suppression", true);
    }

    // ------------------------------------------------------------------ outils

    static String obligatoire(String valeur, String nom) {
        if (valeur == null || valeur.isBlank()) {
            throw new RegleMetierException(nom + " est obligatoire.");
        }
        return valeur.strip();
    }

    static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.strip();
    }
}
