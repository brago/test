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
                   String carte, String compte) {
    }

    record Operation(long id, long sessionId, String terminal, String type, String statut, LocalDateTime dateHeure,
                     String carte, Long montant, String devise, String codeReponse, String libelleReponse,
                     String compte, String reference, List<Integer> billetsPresentes, boolean billetsPris,
                     List<String> incidents) {
    }

    record Session(long id, String nomFichier, String terminal, LocalDate date, LocalTime heureDebut, String carte,
                   List<Integer> billetsPresentes, boolean billetsPris, Map<String, Object> compteurs,
                   List<String> incidents, boolean complete, String texte, List<Operation> operations) {
    }

    record Evenement(long id, String terminal, LocalDateTime dateHeure, List<String> actions,
                     List<Map<String, List<Integer>>> releves, Map<String, Integer> especesAjoutees, String texte) {
    }

    PageResultat<Operation> rechercher(Critere critere, int page, int taille);

    long compter(Critere critere);

    void parcourir(Critere critere, Consumer<Operation> consommateur);

    Optional<Session> session(long id);

    List<Evenement> evenements(LocalDate du, LocalDate au, String terminal);

    List<String> terminaux();
}
