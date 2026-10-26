package com.rapmonet.reclamation.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.Alerte.Niveau;
import com.rapmonet.alertes.SourceAlertes;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Réclamations porteurs dont l'échéance de traitement est dépassée, et réclamations en cours. */
@Component
class AlertesReclamations implements SourceAlertes {

    private final ReclamationsService reclamations;

    AlertesReclamations(ReclamationsService reclamations) {
        this.reclamations = reclamations;
    }

    @Override
    public List<Alerte> alertes(LocalDate aujourdhui) {
        long[] n = reclamations.aTraiter(aujourdhui);
        return List.of(
                new Alerte("RECLAMATIONS_EN_RETARD", Niveau.ATTENTION, "Réclamations porteurs en retard",
                        "Échéance de traitement dépassée : répondre au client ou clore la réclamation.", n[1], null,
                        "RECLAMATION_SUIVI", "/reclamations/registre"),
                new Alerte("RECLAMATIONS_A_TRAITER", Niveau.INFO, "Réclamations porteurs à traiter",
                        "Réclamations ouvertes ou en cours.", n[0] - n[1], null, "RECLAMATION_SUIVI", "/reclamations/registre"));
    }
}
