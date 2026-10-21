package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.util.List;

/** Événement publié après l'intégration de fichiers LIS (après validation de la transaction). */
public record FichiersLisIntegres(List<Long> fichiers, List<LocalDate> journees) {
}
