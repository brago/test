package com.rapmonet.chargeback.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.Alerte.Niveau;
import com.rapmonet.alertes.SourceAlertes;
import com.rapmonet.chargeback.internal.ChargebacksService.Critere;
import com.rapmonet.chargeback.internal.ChargebacksService.Dossier;
import com.rapmonet.chargeback.internal.ChargebacksService.Sens;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Chargebacks non régularisés proches de leur échéance ou devenus définitifs. */
@Component
class AlertesChargebacks implements SourceAlertes {

    /** Une échéance à 7 jours ou moins déclenche l'alerte. */
    static final int JOURS_AVANT_ECHEANCE = 7;

    private final ChargebacksService service;

    AlertesChargebacks(ChargebacksService service) {
        this.service = service;
    }

    @Override
    public List<Alerte> alertes(LocalDate aujourdhui) {
        List<Dossier> recus = new ArrayList<>();
        List<Dossier> emis = new ArrayList<>();
        service.parcourir(new Critere(Sens.ACQUISITION, null, null, null, "NON", false), recus::add);
        service.parcourir(new Critere(Sens.EMISSION, null, null, null, "NON", false), emis::add);
        Predicate<Dossier> proche = d -> "EN_ATTENTE".equals(d.etat()) && d.echeance() != null
                && ChronoUnit.DAYS.between(aujourdhui, d.echeance()) <= JOURS_AVANT_ECHEANCE;
        Predicate<Dossier> echu = d -> "DELAI_ECOULE".equals(d.etat());
        return List.of(
                alerte("CHARGEBACK_RECU_ECHEANCE", Niveau.CRITIQUE, "Chargebacks reçus à traiter avant échéance",
                        "Échéance dans " + JOURS_AVANT_ECHEANCE + " jours ou moins : représenter (billets pris) ou accepter.",
                        recus, proche, "CHARGEBACK_ACQUISITION", "/chargeback/acquisition"),
                alerte("CHARGEBACK_RECU_DEFINITIF", Niveau.ATTENTION, "Chargebacks reçus devenus définitifs",
                        "Délai de représentation dépassé : à régulariser.", recus, echu,
                        "CHARGEBACK_ACQUISITION", "/chargeback/acquisition"),
                alerte("CHARGEBACK_EMIS_ACQUIS", Niveau.ATTENTION, "Chargebacks émis acquis à régulariser",
                        "Pas de représentation dans le délai : passer l'écriture et régulariser.", emis, echu,
                        "CHARGEBACK_EMISSION", "/chargeback/emission"));
    }

    private static Alerte alerte(String code, Niveau niveau, String titre, String detail, List<Dossier> dossiers,
                                 Predicate<Dossier> filtre, String menu, String lien) {
        List<Dossier> l = dossiers.stream().filter(filtre).toList();
        long montant = l.stream().mapToLong(d -> d.montant() == null ? 0 : d.montant()).sum();
        return new Alerte(code, niveau, titre, detail, l.size(), montant, menu, lien);
    }
}
