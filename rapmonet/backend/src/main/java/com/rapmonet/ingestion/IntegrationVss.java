package com.rapmonet.ingestion;

import com.rapmonet.ingestion.RapportIntegration.AnomalieLis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Intégration des fichiers de rapports VSS (EP-747 / VX). */
public interface IntegrationVss {

    /** Montants clés du fichier, tirés des rapports VSS-110 et VSS-900-S. */
    record SyntheseVss(String deviseReglement, BigDecimal montantNet, Integer nbTransactions,
                       String deviseCompensation, BigDecimal montantCompensation) {
    }

    record RapportLu(String code, String titre, int nbLignes, boolean sansDonnees, boolean ignore) {
    }

    record Resultat(boolean integre, Long id, String nom, LocalDate dateTraitement, String sreId, String sreNom,
                    SyntheseVss synthese, List<RapportLu> rapports, List<AnomalieLis> anomalies) {
    }

    record FichierVss(long id, String nom, LocalDate dateTraitement, String sreId, String sreNom, int nbRapports,
                      int nbLignes, SyntheseVss synthese, int nbAvertissements, OffsetDateTime integreLe,
                      String integrePar) {
    }

    Resultat integrer(String nom, byte[] contenu, String utilisateur);

    List<FichierVss> lister();

    List<AnomalieLis> anomalies(long fichierId);

    void annuler(long fichierId);
}
