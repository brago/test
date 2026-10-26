package com.rapmonet.parametrage.internal;

import com.rapmonet.commun.CategorieConservation;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conservation des données : durée par catégorie (au moins la durée minimale de la catégorie), aperçu de ce qui serait
 * purgé, purge manuelle motivée ou planifiée chaque mois. Chaque catégorie est purgée dans sa propre transaction ; le
 * détail de chaque purge est conservé (purge_execution) et tracé dans l'audit.
 */
@Service
public class ConservationService {

    private static final Logger LOG = LoggerFactory.getLogger(ConservationService.class);
    static final String UTILISATEUR_PLANIFIE = "batch";
    private static final int DUREE_MAX_MOIS = 600;

    public record EtatCategorie(String code, String libelle, String description, int dureeMinimaleMois,
                                int dureeParDefautMois, int dureeMois, LocalDate limite, long total, LocalDate plusAncienne,
                                long aPurger) {
    }

    public record LigneExecution(String code, String libelle, int dureeMois, LocalDate limite, long supprimes, String erreur) {
    }

    public record Execution(long id, String declencheur, String lancePar, String motif, OffsetDateTime debut,
                            OffsetDateTime fin, String statut, List<LigneExecution> detail) {
    }

    public record Planification(boolean planifiee, int jour) {
    }

    public record Etat(List<EtatCategorie> categories, Planification planification, List<Execution> dernieresExecutions) {
    }

    private final List<CategorieConservation> categories;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final JournalAudit audit;
    private final TransactionTemplate transaction;
    private final AtomicBoolean enCours = new AtomicBoolean();

    ConservationService(List<CategorieConservation> categories, JdbcClient jdbc, JsonMapper json, JournalAudit audit,
                        PlatformTransactionManager transactions) {
        this.categories = categories.stream().sorted(Comparator.comparing(CategorieConservation::libelle)).toList();
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Transactional(readOnly = true)
    public Etat etat(LocalDate aujourdhui) {
        List<EtatCategorie> l = new ArrayList<>();
        for (CategorieConservation c : categories) {
            int duree = duree(c);
            LocalDate limite = aujourdhui.minusMonths(duree);
            l.add(new EtatCategorie(c.code(), c.libelle(), c.description(), c.dureeMinimaleMois(), c.dureeParDefautMois(),
                    duree, limite, c.total(), c.plusAncienne(), c.aPurger(limite)));
        }
        return new Etat(l, planification(), executions());
    }

    @Transactional
    public void modifierDuree(String code, int mois, String motif, String utilisateur) {
        CategorieConservation c = categorie(code);
        String texte = exigerMotif(motif);
        if (mois < c.dureeMinimaleMois() || mois > DUREE_MAX_MOIS) {
            throw new RegleMetierException(c.libelle() + " : durée attendue entre " + c.dureeMinimaleMois() + " et "
                    + DUREE_MAX_MOIS + " mois (minimum de la catégorie : " + annees(c.dureeMinimaleMois()) + ").");
        }
        int avant = duree(c);
        if (avant == mois) {
            throw new RegleMetierException("La durée est inchangée.");
        }
        enregistrer(cle(c), String.valueOf(mois), "Durée de conservation (mois) : " + c.libelle(), String.valueOf(avant),
                texte, utilisateur);
        audit.enregistrerPour(utilisateur, ActionsAudit.CONSERVATION, c.libelle(), avant + " → " + mois + " mois : " + texte, true);
    }

    @Transactional
    public Planification modifierPlanification(boolean planifiee, int jour, String motif, String utilisateur) {
        String texte = exigerMotif(motif);
        if (jour < 1 || jour > 28) {
            throw new RegleMetierException("Jour du mois attendu entre 1 et 28.");
        }
        Planification avant = planification();
        enregistrer("conservation.purge.planifiee", String.valueOf(planifiee), null, String.valueOf(avant.planifiee()), texte, utilisateur);
        enregistrer("conservation.purge.jour", String.valueOf(jour), null, String.valueOf(avant.jour()), texte, utilisateur);
        audit.enregistrerPour(utilisateur, ActionsAudit.CONSERVATION, "Purge automatique",
                (planifiee ? "activée le " + jour + " du mois" : "désactivée") + " : " + texte, true);
        return planification();
    }

    /** Purge toutes les catégories ; une catégorie en erreur n'empêche pas les autres. */
    public Execution purger(String declencheur, String motif, String utilisateur, LocalDate aujourdhui) {
        String texte = "PLANIFIE".equals(declencheur) ? "Purge automatique mensuelle" : exigerMotif(motif);
        if (!enCours.compareAndSet(false, true)) {
            throw new RegleMetierException("Une purge est déjà en cours.");
        }
        try {
            long id = jdbc.sql("insert into purge_execution (declencheur, lance_par, motif, statut) values (?, ?, ?, 'EN_COURS') returning id")
                    .params(declencheur, utilisateur, texte).query(Long.class).single();
            List<LigneExecution> detail = new ArrayList<>();
            boolean erreur = false;
            for (CategorieConservation c : categories) {
                int duree = duree(c);
                LocalDate limite = aujourdhui.minusMonths(duree);
                try {
                    Long n = transaction.execute(s -> c.purger(limite));
                    detail.add(new LigneExecution(c.code(), c.libelle(), duree, limite, n == null ? 0 : n, null));
                } catch (RuntimeException e) {
                    LOG.error("Purge de la catégorie {} en erreur", c.code(), e);
                    erreur = true;
                    detail.add(new LigneExecution(c.code(), c.libelle(), duree, limite, 0, e.getMessage()));
                }
            }
            jdbc.sql("update purge_execution set fin = now(), statut = ?, detail = ?::jsonb where id = ?")
                    .params(erreur ? "ERREUR" : "TERMINE", json.writeValueAsString(detail), id).update();
            long total = detail.stream().mapToLong(LigneExecution::supprimes).sum();
            audit.enregistrerPour(utilisateur, ActionsAudit.PURGE, "Purge n° " + id, total + " élément(s) supprimé(s) : " + texte, !erreur);
            return executions().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
        } finally {
            enCours.set(false);
        }
    }

    /** Purge planifiée due aujourd'hui (activée, jour du mois atteint, pas encore exécutée ce mois-ci). */
    boolean purgePlanifieeDue(LocalDate aujourdhui) {
        Planification p = planification();
        if (!p.planifiee() || aujourdhui.getDayOfMonth() < p.jour()) {
            return false;
        }
        return jdbc.sql("select count(*) from purge_execution where declencheur = 'PLANIFIE' and debut >= ?")
                .param(aujourdhui.withDayOfMonth(1).atStartOfDay()).query(Integer.class).single() == 0;
    }

    private Planification planification() {
        Map<String, String> v = new java.util.HashMap<>();
        jdbc.sql("select cle, valeur from parametre_application where cle in ('conservation.purge.planifiee', 'conservation.purge.jour')")
                .query(rs -> {
                    v.put(rs.getString(1), rs.getString(2));
                });
        int jour;
        try {
            jour = Integer.parseInt(v.getOrDefault("conservation.purge.jour", "1").strip());
        } catch (NumberFormatException e) {
            jour = 1;
        }
        return new Planification(Boolean.parseBoolean(v.getOrDefault("conservation.purge.planifiee", "false").strip()), jour);
    }

    private List<Execution> executions() {
        return jdbc.sql("select * from purge_execution order by id desc limit 12").query((rs, n) -> new Execution(
                rs.getLong("id"), rs.getString("declencheur"), rs.getString("lance_par"), rs.getString("motif"),
                rs.getObject("debut", OffsetDateTime.class), rs.getObject("fin", OffsetDateTime.class), rs.getString("statut"),
                json.readValue(rs.getString("detail"), new TypeReference<List<LigneExecution>>() { }))).list();
    }

    private int duree(CategorieConservation c) {
        return jdbc.sql("select valeur from parametre_application where cle = ?").param(cle(c)).query(String.class).optional()
                .map(v -> {
                    try {
                        return Math.max(Integer.parseInt(v.strip()), c.dureeMinimaleMois());
                    } catch (NumberFormatException e) {
                        return c.dureeParDefautMois();
                    }
                }).orElse(c.dureeParDefautMois());
    }

    private void enregistrer(String cle, String valeur, String description, String avant, String motif, String utilisateur) {
        jdbc.sql("""
                        insert into parametre_application (cle, valeur, description, modifie_par) values (?, ?, ?, ?)
                        on conflict (cle) do update set valeur = excluded.valeur, modifie_par = excluded.modifie_par, modifie_le = now()""")
                .params(cle, valeur, description, utilisateur).update();
        jdbc.sql("insert into parametre_historique (cle, avant, apres, motif, modifie_par) values (?, ?, ?, ?, ?)")
                .params(cle, avant, valeur, motif, utilisateur).update();
    }

    private CategorieConservation categorie(String code) {
        return categories.stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new RegleMetierException("Catégorie inconnue : " + code));
    }

    private static String cle(CategorieConservation c) {
        return "conservation." + c.code().toLowerCase() + ".mois";
    }

    private static String annees(int mois) {
        return mois % 12 == 0 ? (mois / 12) + " an(s)" : mois + " mois";
    }

    private static String exigerMotif(String motif) {
        String texte = motif == null ? "" : motif.strip();
        if (texte.length() < 5 || texte.length() > 500) {
            throw new RegleMetierException("Indiquez le motif (5 à 500 caractères).");
        }
        return texte;
    }
}
