package com.rapmonet.gab.internal;

import com.rapmonet.ingestion.ConsultationJournaux;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ce que les journaux électroniques disent d'un passage du gabiste : compteurs au moment de l'arrêté
 * (avant remise à zéro) et billets ajoutés (approvisionnement), par type de billet 1 à 4.
 */
@Component
public class JournalGab {

    /**
     * @param cassettes billets dans les cassettes avant la remise à zéro (compteur CASSETTE), null si non imprimé
     * @param rejets    billets rejetés / retirés avant la remise à zéro (compteur REJECTED), null si non imprimé
     * @param ajoutes   billets ajoutés (ADD CASH), null si pas d'ajout d'espèces
     */
    public record Passage(LocalDateTime dateHeure, List<String> actions, List<Integer> cassettes, List<Integer> rejets,
                          List<Integer> ajoutes) {
    }

    private final ConsultationJournaux journaux;

    JournalGab(ConsultationJournaux journaux) {
        this.journaux = journaux;
    }

    /** Passages superviseur du GAB le jour donné, du plus ancien au plus récent. */
    public List<Passage> passages(String terminal, LocalDate jour) {
        return journaux.evenements(jour, jour, terminal).stream()
                .filter(e -> e.dateHeure() != null)
                .sorted(Comparator.comparing(ConsultationJournaux.Evenement::dateHeure))
                .map(JournalGab::passage).toList();
    }

    /** Couverture des journaux intégrés sur la période : jours avec journal / jours de la période. */
    public record Couverture(int joursCouverts, int joursPeriode) {
    }

    public Couverture couverture(String terminal, LocalDate du, LocalDate au) {
        return new Couverture(journaux.joursCouverts(terminal, du, au), (int) (au.toEpochDay() - du.toEpochDay()) + 1);
    }

    public long montantDistribue(String terminal, LocalDateTime apres, LocalDateTime jusqua) {
        return journaux.montantDistribue(terminal, apres, jusqua);
    }

    private static Passage passage(ConsultationJournaux.Evenement e) {
        // Le premier relevé est imprimé à la remise à zéro : il donne l'état au moment de l'arrêté.
        Map<String, List<Integer>> avant = e.actions().contains("REMISE_A_ZERO_COMPTEURS") && !e.releves().isEmpty()
                ? e.releves().getFirst() : null;
        List<Integer> ajoutes = null;
        if (e.actions().contains("AJOUT_ESPECES") && !e.especesAjoutees().isEmpty()) {
            ajoutes = new ArrayList<>();
            for (int t = 1; t <= 4; t++) {
                ajoutes.add(e.especesAjoutees().getOrDefault("TYPE" + t, 0));
            }
        }
        return new Passage(e.dateHeure(), e.actions(), avant == null ? null : quatre(avant.get("CASSETTE")),
                avant == null ? null : quatre(avant.get("REJECTED")), ajoutes);
    }

    private static List<Integer> quatre(List<Integer> valeurs) {
        if (valeurs == null) {
            return null;
        }
        List<Integer> l = new ArrayList<>(valeurs);
        while (l.size() < 4) {
            l.add(0);
        }
        return List.copyOf(l.subList(0, 4));
    }
}
