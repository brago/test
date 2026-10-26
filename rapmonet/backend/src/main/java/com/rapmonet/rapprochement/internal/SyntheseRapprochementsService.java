package com.rapmonet.rapprochement.internal;

import com.rapmonet.rapprochement.SyntheseRapprochements;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
class SyntheseRapprochementsService implements SyntheseRapprochements {

    private final RapprochementGabLis rapprochement;

    SyntheseRapprochementsService(RapprochementGabLis rapprochement) {
        this.rapprochement = rapprochement;
    }

    @Override
    public Synthese synthese(LocalDate du, LocalDate au) {
        var s = rapprochement.rapprocher(du, au, null, RapprochementGabLis.Vue.ECARTS).synthese();
        return new Synthese(s.retraitsConfreres(), s.rapproches(), s.nonRemontes(), s.montantNonRemonte(),
                s.avisSansRetrait(), s.montantAvisSansRetrait(), s.enAttente(), s.ecartsATraiter());
    }
}
