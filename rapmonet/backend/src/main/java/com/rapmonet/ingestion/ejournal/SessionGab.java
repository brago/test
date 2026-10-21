package com.rapmonet.ingestion.ejournal;

import java.time.LocalTime;
import java.util.List;

/**
 * Session client : de « *TRANSACTION START* » à « TRANSACTION END ».
 * Une session peut contenir plusieurs opérations (ex. solde puis retrait) ou aucune
 * (code PIN erroné, délai dépassé, annulation…).
 *
 * @param billetsPresentes billets présentés par type de cassette (« NOTES PRESENTED »), null si aucun
 * @param billetsPris      le client a pris les billets (« NOTES TAKEN »)
 * @param texte            extrait du journal (texte nettoyé de la session)
 * @param complete         la ligne « TRANSACTION END » a été trouvée
 */
public record SessionGab(int rang, int numeroLigne, LocalTime heureDebut, String carte,
                         List<OperationGab> operations, List<Integer> billetsPresentes, boolean billetsPris,
                         CompteursCassettes compteurs, List<String> incidents, String texte, boolean complete) {
}
