package com.rapmonet.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Consultation des rapports VSS intégrés. */
public interface ConsultationVss {

    record Valeur(BigDecimal nombre, String sens, String texte) {
    }

    record ResumeRapport(long id, long fichierId, String nomFichier, String code, String libelleType, String titre,
                         LocalDate dateTraitement, String sreId, String sreNom, int nbPages, boolean sansDonnees,
                         int nbLignes) {
    }

    record Ligne(int rang, int page, int niveau, String chemin, String libelle, String deviseReglement,
                 String deviseCompensation, Map<String, Valeur> valeurs) {
    }

    record Rapport(ResumeRapport resume, String rollup, String entiteFonds, List<String> colonnes, List<Ligne> lignes) {
    }

    List<ResumeRapport> rechercher(LocalDate du, LocalDate au, String code);

    Optional<Rapport> rapport(long id);
}
