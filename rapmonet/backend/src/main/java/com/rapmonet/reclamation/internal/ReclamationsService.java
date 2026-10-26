package com.rapmonet.reclamation.internal;

import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.commun.web.RessourceIntrouvableException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registre des réclamations des porteurs : ouverture (avec les opérations concernées), prise en charge, commentaires,
 * clôture motivée (fondée ou non fondée), réouverture. Chaque étape est tracée dans le suivi de la réclamation et
 * dans la piste d'audit. Seule la carte masquée est conservée.
 */
@Service
public class ReclamationsService {

    public static final Map<String, String> OBJETS = new LinkedHashMap<>();
    public static final Map<String, String> CANAUX = new LinkedHashMap<>();
    public static final Map<String, String> STATUTS = new LinkedHashMap<>();

    static {
        OBJETS.put("RETRAIT_NON_DISTRIBUE", "Retrait débité, billets non reçus");
        OBJETS.put("RETRAIT_PARTIEL", "Retrait partiellement distribué");
        OBJETS.put("DEBIT_EN_DOUBLE", "Opération débitée deux fois");
        OBJETS.put("CARTE_CAPTUREE", "Carte capturée par un GAB");
        OBJETS.put("OPERATION_NON_RECONNUE", "Opération non reconnue par le porteur");
        OBJETS.put("PAIEMENT_CONTESTE", "Paiement contesté (commerçant)");
        OBJETS.put("AUTRE", "Autre");
        CANAUX.put("GUICHET", "Guichet");
        CANAUX.put("TELEPHONE", "Téléphone");
        CANAUX.put("COURRIER", "Courrier");
        CANAUX.put("EMAIL", "E-mail");
        STATUTS.put("OUVERTE", "Ouverte");
        STATUTS.put("EN_COURS", "En cours");
        STATUTS.put("FONDEE", "Close – fondée");
        STATUTS.put("NON_FONDEE", "Close – non fondée");
    }

    private static final Set<String> CLOSES = Set.of("FONDEE", "NON_FONDEE");

    /** Opération rattachée : instantané lisible, pour que la fiche reste identique dans le temps. */
    public record OperationLiee(String type, long id, String date, String libelle, Long montant) {
    }

    public record Saisie(String carte, String compte, String client, String contact, String canal, String objet,
                         String description, Long montantConteste, LocalDate dateOperation, List<OperationLiee> operations) {
    }

    public record Resume(long id, String numero, String carte, String compte, String client, String objet,
                         String libelleObjet, String statut, String libelleStatut, LocalDate echeance, boolean enRetard,
                         Long montantConteste, OffsetDateTime creeLe) {
    }

    public record Suivi(String action, String commentaire, String auteur, OffsetDateTime le) {
    }

    public record Reclamation(long id, String numero, String carte, String compte, String client, String contact,
                              String canal, String libelleCanal, String objet, String libelleObjet, String description,
                              Long montantConteste, LocalDate dateOperation, List<OperationLiee> operations, String statut,
                              String libelleStatut, String conclusion, LocalDate echeance, boolean enRetard, String creePar,
                              OffsetDateTime creeLe, String cloturePar, OffsetDateTime clotureLe, List<Suivi> suivi) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final JournalAudit audit;

    ReclamationsService(JdbcClient jdbc, JsonMapper json, JournalAudit audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    @Transactional
    public Reclamation ouvrir(Saisie s, String utilisateur) {
        RecherchePorteur.Carte carte = RecherchePorteur.carte(s.carte(), numero -> null);
        String compte = vide(s.compte());
        if (carte == null && compte == null) {
            throw new RegleMetierException("Indiquez la carte ou le compte du porteur.");
        }
        texte(s.client(), "Le nom du client", 200);
        texte(s.description(), "La description", 2000);
        if (s.canal() == null || !CANAUX.containsKey(s.canal())) {
            throw new RegleMetierException("Canal attendu : " + String.join(", ", CANAUX.keySet()) + ".");
        }
        if (s.objet() == null || !OBJETS.containsKey(s.objet())) {
            throw new RegleMetierException("Objet attendu : " + String.join(", ", OBJETS.keySet()) + ".");
        }
        if (s.montantConteste() != null && s.montantConteste() < 0) {
            throw new RegleMetierException("Le montant contesté ne peut pas être négatif.");
        }
        List<OperationLiee> ops = s.operations() == null ? List.of() : s.operations();
        if (ops.size() > 50) {
            throw new RegleMetierException("50 opérations au plus par réclamation.");
        }
        long seq = jdbc.sql("select nextval('reclamation_numero_seq')").query(Long.class).single();
        String numero = "REC-" + LocalDate.now().getYear() + "-" + String.format("%05d", seq);
        long id = jdbc.sql("""
                        insert into reclamation (numero, carte, compte, client, contact, canal, objet, description,
                            montant_conteste, date_operation, operations, statut, echeance, cree_par)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'OUVERTE', ?, ?) returning id""")
                .params(numero, carte == null ? null : carte.masquee(), compte, s.client().strip(), vide(s.contact()),
                        s.canal(), s.objet(), s.description().strip(), s.montantConteste(), s.dateOperation(),
                        json.writeValueAsString(ops), LocalDate.now().plusDays(delai()), utilisateur)
                .query(Long.class).single();
        suivre(id, "OUVERTURE", OBJETS.get(s.objet()) + (ops.isEmpty() ? "" : " ; " + ops.size() + " opération(s) rattachée(s)"),
                utilisateur);
        audit.enregistrerPour(utilisateur, ActionsAudit.RECLAMATION, numero, "Ouverture : " + OBJETS.get(s.objet()), true);
        return detail(id);
    }

    /** Prise en charge, commentaire, clôture (FONDEE / NON_FONDEE, conclusion obligatoire) ou réouverture. */
    @Transactional
    public Reclamation agir(long id, String action, String commentaire, String utilisateur) {
        Reclamation r = detail(id);
        String texte = vide(commentaire);
        boolean close = CLOSES.contains(r.statut());
        switch (action == null ? "" : action) {
            case "PRISE_EN_CHARGE" -> {
                if (!"OUVERTE".equals(r.statut())) {
                    throw new RegleMetierException("Seule une réclamation ouverte peut être prise en charge.");
                }
                jdbc.sql("update reclamation set statut = 'EN_COURS' where id = ?").param(id).update();
            }
            case "COMMENTAIRE" -> {
                if (texte == null) {
                    throw new RegleMetierException("Le commentaire est vide.");
                }
            }
            case "FONDEE", "NON_FONDEE" -> {
                if (close) {
                    throw new RegleMetierException("La réclamation est déjà close.");
                }
                if (texte == null || texte.length() < 10) {
                    throw new RegleMetierException("Indiquez la conclusion communiquée au client (10 caractères au moins).");
                }
                jdbc.sql("update reclamation set statut = ?, conclusion = ?, cloture_par = ?, cloture_le = now() where id = ?")
                        .params(action, texte, utilisateur, id).update();
            }
            case "REOUVERTURE" -> {
                if (!close) {
                    throw new RegleMetierException("Seule une réclamation close peut être rouverte.");
                }
                if (texte == null) {
                    throw new RegleMetierException("Indiquez le motif de la réouverture.");
                }
                jdbc.sql("update reclamation set statut = 'EN_COURS', cloture_par = null, cloture_le = null, echeance = ? where id = ?")
                        .params(LocalDate.now().plusDays(delai()), id).update();
            }
            default -> throw new RegleMetierException("Action inconnue.");
        }
        if (texte != null && texte.length() > 2000) {
            throw new RegleMetierException("Le texte est limité à 2 000 caractères.");
        }
        String suiviAction = action.equals("FONDEE") || action.equals("NON_FONDEE") ? "CLOTURE" : action;
        suivre(id, suiviAction, action.equals("FONDEE") ? "Fondée : " + texte : action.equals("NON_FONDEE")
                ? "Non fondée : " + texte : texte, utilisateur);
        audit.enregistrerPour(utilisateur, ActionsAudit.RECLAMATION, r.numero(), action + (texte == null ? "" : " : " + texte), true);
        return detail(id);
    }

    @Transactional(readOnly = true)
    public List<Resume> lister(String statut, String recherche) {
        List<Object> p = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        if ("A_TRAITER".equals(statut)) {
            w.append(" and statut in ('OUVERTE', 'EN_COURS')");
        } else if (statut != null && STATUTS.containsKey(statut)) {
            w.append(" and statut = ?");
            p.add(statut);
        }
        if (vide(recherche) != null) {
            String q = "%" + recherche.strip().toLowerCase() + "%";
            w.append(" and (lower(numero) like ? or lower(client) like ? or coalesce(compte, '') like ? or coalesce(carte, '') like ?)");
            p.add(q);
            p.add(q);
            p.add(q);
            p.add(q);
        }
        return jdbc.sql("select * from reclamation" + w + " order by (statut in ('OUVERTE', 'EN_COURS')) desc, echeance, id desc limit 500")
                .params(p).query((rs, n) -> resume(rs)).list();
    }

    /** Réclamations déjà enregistrées pour une carte masquée ou un compte. */
    @Transactional(readOnly = true)
    public List<Resume> duPorteur(String carteMasquee, String compte) {
        if (carteMasquee == null && compte == null) {
            return List.of();
        }
        return jdbc.sql("""
                        select * from reclamation where (cast(? as varchar) is not null and carte = ?)
                            or (cast(? as varchar) is not null and compte = ?) order by id desc limit 50""")
                .params(carteMasquee, carteMasquee, compte, compte).query((rs, n) -> resume(rs)).list();
    }

    @Transactional(readOnly = true)
    public Reclamation detail(long id) {
        List<Suivi> suivi = jdbc.sql("select action, commentaire, auteur, le from reclamation_suivi where reclamation_id = ? order by id")
                .param(id).query(Suivi.class).list();
        return jdbc.sql("select * from reclamation where id = ?").param(id).query((rs, n) -> {
            String statut = rs.getString("statut");
            LocalDate echeance = rs.getObject("echeance", LocalDate.class);
            return new Reclamation(rs.getLong("id"), rs.getString("numero"), rs.getString("carte"), rs.getString("compte"),
                    rs.getString("client"), rs.getString("contact"), rs.getString("canal"), CANAUX.get(rs.getString("canal")),
                    rs.getString("objet"), OBJETS.get(rs.getString("objet")), rs.getString("description"),
                    (Long) rs.getObject("montant_conteste"), rs.getObject("date_operation", LocalDate.class),
                    json.readValue(rs.getString("operations"), new TypeReference<List<OperationLiee>>() { }), statut,
                    STATUTS.get(statut), rs.getString("conclusion"), echeance, enRetard(statut, echeance),
                    rs.getString("cree_par"), rs.getObject("cree_le", OffsetDateTime.class), rs.getString("cloture_par"),
                    rs.getObject("cloture_le", OffsetDateTime.class), suivi);
        }).optional().orElseThrow(() -> new RessourceIntrouvableException("Réclamation inconnue."));
    }

    /** Réclamations non closes : nombre total et nombre en retard. */
    @Transactional(readOnly = true)
    public long[] aTraiter(LocalDate aujourdhui) {
        return jdbc.sql("""
                        select count(*), count(*) filter (where echeance < ?) from reclamation
                        where statut in ('OUVERTE', 'EN_COURS')""")
                .param(aujourdhui).query((rs, n) -> new long[] { rs.getLong(1), rs.getLong(2) }).single();
    }

    private Resume resume(java.sql.ResultSet rs) throws java.sql.SQLException {
        String statut = rs.getString("statut");
        LocalDate echeance = rs.getObject("echeance", LocalDate.class);
        return new Resume(rs.getLong("id"), rs.getString("numero"), rs.getString("carte"), rs.getString("compte"),
                rs.getString("client"), rs.getString("objet"), OBJETS.get(rs.getString("objet")), statut, STATUTS.get(statut),
                echeance, enRetard(statut, echeance), (Long) rs.getObject("montant_conteste"),
                rs.getObject("cree_le", OffsetDateTime.class));
    }

    private static boolean enRetard(String statut, LocalDate echeance) {
        return !CLOSES.contains(statut) && echeance != null && echeance.isBefore(LocalDate.now());
    }

    private void suivre(long id, String action, String commentaire, String utilisateur) {
        jdbc.sql("insert into reclamation_suivi (reclamation_id, action, commentaire, auteur) values (?, ?, ?, ?)")
                .params(id, action, commentaire, utilisateur).update();
    }

    private int delai() {
        return jdbc.sql("select valeur from parametre_application where cle = 'reclamation.delai_jours'").query(String.class)
                .optional().map(v -> {
                    try {
                        return Integer.parseInt(v.strip());
                    } catch (NumberFormatException e) {
                        return 15;
                    }
                }).orElse(15);
    }

    private static void texte(String v, String libelle, int max) {
        if (v == null || v.isBlank() || v.strip().length() > max) {
            throw new RegleMetierException(libelle + " est obligatoire (" + max + " caractères au plus).");
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
