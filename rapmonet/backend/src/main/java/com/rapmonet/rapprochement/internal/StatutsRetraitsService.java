package com.rapmonet.rapprochement.internal;

import com.rapmonet.rapprochement.StatutsRetraits;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Relance le rapprochement GAB ↔ LIS sur le jour et le GAB de chaque retrait (au plus 31 couples GAB / jour). */
@Service
class StatutsRetraitsService implements StatutsRetraits {

    private static final int MAX_JOURS = 31;
    private static final Map<String, String> TRAITEMENTS = Map.of("A_TRAITER", "À traiter", "JUSTIFIE", "Justifié",
            "REGULARISE", "Régularisé");

    private final RapprochementGabLis rapprochement;

    StatutsRetraitsService(RapprochementGabLis rapprochement) {
        this.rapprochement = rapprochement;
    }

    @Override
    public Map<Long, Statut> statuts(List<Retrait> retraits) {
        Map<String, List<Retrait>> groupes = retraits.stream().filter(r -> r.dateHeure() != null && r.terminal() != null)
                .collect(Collectors.groupingBy(r -> r.terminal() + "|" + r.dateHeure().toLocalDate(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, Statut> statuts = new HashMap<>();
        groupes.entrySet().stream().limit(MAX_JOURS).forEach(e -> {
            Retrait premier = e.getValue().get(0);
            LocalDate jour = premier.dateHeure().toLocalDate();
            Map<String, RapprochementGabLis.Ligne> lignes = new HashMap<>();
            rapprochement.rapprocher(jour, jour, premier.terminal(), RapprochementGabLis.Vue.TOUS).lignes()
                    .forEach(l -> lignes.put(l.cle(), l));
            for (Retrait r : e.getValue()) {
                String cle = "EJ:" + r.terminal() + "|" + r.dateHeure() + "|" + r.carte() + "|" + r.reference() + "|" + r.montant();
                RapprochementGabLis.Ligne l = lignes.get(cle);
                statuts.put(r.id(), l == null
                        ? new Statut("Carte de la banque (hors GIM, à voir dans Amplitude)", null, null, null)
                        : new Statut(l.libelleResultat(), l.traitement() == null ? null : TRAITEMENTS.get(l.traitement()),
                        l.commentaire(), l.autorisation()));
            }
        });
        return statuts;
    }
}
