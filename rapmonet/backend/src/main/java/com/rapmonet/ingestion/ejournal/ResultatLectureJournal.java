package com.rapmonet.ingestion.ejournal;

import com.rapmonet.ingestion.moteur.Anomalie;
import com.rapmonet.ingestion.moteur.Gravite;

import java.util.List;

/** @param terminal identifiant du GAB lu dans les tickets (« NUM GAB »), null si absent */
public record ResultatLectureJournal(String terminal, List<SessionGab> sessions, List<EvenementGab> evenements,
                                     List<Anomalie> anomalies) {

    public boolean estBloquant() {
        return anomalies.stream().anyMatch(a -> a.gravite() == Gravite.BLOQUANTE);
    }

    public List<OperationGab> operations() {
        return sessions.stream().flatMap(s -> s.operations().stream()).toList();
    }
}
