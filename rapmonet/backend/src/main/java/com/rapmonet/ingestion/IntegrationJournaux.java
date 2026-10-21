package com.rapmonet.ingestion;

import com.rapmonet.ingestion.RapportIntegration.AnomalieLis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Intégration des journaux électroniques des GAB. Chaque fichier est intégré indépendamment. */
public interface IntegrationJournaux {

    record Resultat(String nom, boolean integre, Long id, String terminal, LocalDate dateDebut, LocalDate dateFin,
                    int nbSessions, int nbOperations, int nbDoublons, int nbRetraitsApprouves, long montantRetraits,
                    int nbEvenements, List<AnomalieLis> anomalies) {
    }

    record FichierJournal(long id, String nom, String terminal, LocalDate dateDebut, LocalDate dateFin, int nbSessions,
                          int nbOperations, int nbDoublons, int nbRetraitsApprouves, long montantRetraits,
                          int nbEvenements, int nbAvertissements, OffsetDateTime integreLe, String integrePar) {
    }

    Resultat integrer(String nom, byte[] contenu, String utilisateur);

    List<FichierJournal> lister();

    List<AnomalieLis> anomalies(long fichierId);

    void annuler(long fichierId);
}
