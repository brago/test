package com.rapmonet.rapprochement.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.Alerte.Niveau;
import com.rapmonet.alertes.SourceAlertes;
import com.rapmonet.rapprochement.internal.RapprochementGabLis.Ligne;
import com.rapmonet.rapprochement.internal.RapprochementGabLis.Resultat;
import com.rapmonet.rapprochement.internal.RapprochementGabLis.Vue;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Écarts GAB / LIS à traiter sur les 60 derniers jours. */
@Component
class AlertesRapprochement implements SourceAlertes {

    static final int JOURS = 60;

    private final RapprochementGabLis service;

    AlertesRapprochement(RapprochementGabLis service) {
        this.service = service;
    }

    @Override
    public List<Alerte> alertes(LocalDate aujourdhui) {
        List<Ligne> lignes = service.rapprocher(aujourdhui.minusDays(JOURS), aujourdhui, null, Vue.ECARTS).lignes();
        return List.of(
                alerte(lignes, Resultat.RETRAIT_NON_REMONTE, "RAPPRO_NON_REMONTES", Niveau.ATTENTION,
                        "Retraits confrères non remontés par le GIM",
                        "Montant à réclamer au GIM (" + JOURS + " derniers jours), écarts non traités."),
                alerte(lignes, Resultat.AVIS_SANS_RETRAIT, "RAPPRO_AVIS_SANS_RETRAIT", Niveau.ATTENTION,
                        "Avis LIS sans retrait au journal", "Écarts non traités sur les " + JOURS + " derniers jours."),
                alerte(lignes, Resultat.JOURNAL_ABSENT, "RAPPRO_JOURNAL_ABSENT", Niveau.INFO,
                        "Avis LIS sur des GAB sans journal", "Intégrer les journaux manquants pour les rapprocher."));
    }

    private static Alerte alerte(List<Ligne> lignes, Resultat r, String code, Niveau niveau, String titre, String detail) {
        List<Ligne> l = lignes.stream().filter(x -> x.resultat() == r
                && (x.traitement() == null || "A_TRAITER".equals(x.traitement()))).toList();
        return new Alerte(code, niveau, titre, detail, l.size(), l.stream().mapToLong(Ligne::montant).sum(),
                "RAPPROCHEMENT_GAB_LIS", "/rapprochement/gab-lis");
    }
}
