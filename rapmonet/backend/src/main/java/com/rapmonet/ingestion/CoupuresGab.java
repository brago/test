package com.rapmonet.ingestion;

import java.util.List;
import java.util.Optional;

/**
 * Valeur des billets de type 1 à 4 d'un GAB (0 = type non chargé). Fournie par le module GAB
 * quand le GAB est paramétré ; sinon le paramètre global ejournal.coupures s'applique.
 */
public interface CoupuresGab {

    Optional<List<Long>> coupuresParType(String terminal);
}
