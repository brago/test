package com.rapmonet.alertes;

import java.time.LocalDate;
import java.util.List;

/** Fournit les alertes d'un module ; implémentée dans le module concerné. */
public interface SourceAlertes {

    List<Alerte> alertes(LocalDate aujourdhui);
}
