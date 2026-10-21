package com.rapmonet.ingestion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/** Fichier LIS intégré. */
public record FichierLis(long id, String nom, TypeLis typeLis, LocalDate journee, Map<String, Object> entete,
                         int nbEnregistrements, int nbTransactions, Map<String, Integer> repartitionTc,
                         int nbAvertissements, OffsetDateTime integreLe) {
}
