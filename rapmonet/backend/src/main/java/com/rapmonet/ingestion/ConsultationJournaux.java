package com.rapmonet.ingestion;

import com.rapmonet.commun.PageResultat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Consultation des opérations, sessions et événements des journaux GAB. */
public interface ConsultationJournaux {

    record Critere(LocalDate du, LocalDate au, String terminal, String type, String statut, String codeReponse,
                   String carte, String compte, boolean ecartBillets) {
    }

    record Operation(long id, long sessionId, String terminal, String type, String statut, LocalDateTime dateHeure,
                     String carte, Long montant, String devise, String codeReponse, String libelleReponse,
                     String compte, String reference, List<Integer> billetsPresentes, boolean billetsPris,
                     List<String> incidents, Long montantBillets, Long ecartBillets) {
        // montantBillets : billets présentés × coupures ; ecartBillets : montantBillets − montant (null si non calculable)
    }

    record Session(long id, String nomFichier, String terminal, LocalDate date, LocalTime heureDebut, String carte,
                   List<Integer> billetsPresentes, boolean billetsPris, Map<String, Object> compteurs,
                   List<String> incidents, boolean complete, String texte, List<Operation> operations,
                   List<Long> coupures) {
    }

    record Evenement(long id, String terminal, LocalDateTime dateHeure, List<String> actions,
                     List<Map<String, List<Integer>>> releves, Map<String, Integer> especesAjoutees, String texte) {
    }

    /** Montant des retraits distribués (billets pris) par le GAB entre deux instants (après le premier instant, jusqu'au second inclus). */
    long montantDistribue(String terminal, LocalDateTime apres, LocalDateTime jusqua);

    /** Nombre de jours de la période pour lesquels un journal du GAB est intégré (au moins une session). */
    int joursCouverts(String terminal, LocalDate du, LocalDate au);

    PageResultat<Operation> rechercher(Critere critere, int page, int taille);

    long compter(Critere critere);

    void parcourir(Critere critere, Consumer<Operation> consommateur);

    Optional<Session> session(long id);

    List<Evenement> evenements(LocalDate du, LocalDate au, String terminal);

    List<String> terminaux();
}
