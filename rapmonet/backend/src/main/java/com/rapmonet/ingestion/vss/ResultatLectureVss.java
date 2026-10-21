package com.rapmonet.ingestion.vss;

import com.rapmonet.ingestion.moteur.Anomalie;
import com.rapmonet.ingestion.moteur.Gravite;

import java.util.List;

public record ResultatLectureVss(List<RapportVss> rapports, List<Anomalie> anomalies) {

    public boolean estBloquant() {
        return anomalies.stream().anyMatch(a -> a.gravite() == Gravite.BLOQUANTE);
    }
}
