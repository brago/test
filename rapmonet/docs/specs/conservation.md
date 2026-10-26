# Conservation des données

Écran *Paramétrage › Conservation des données* (menu `PARAMETRAGE_CONSERVATION`), service
`parametrage.internal.ConservationService`. Chaque module déclare ses catégories en implémentant
`commun.CategorieConservation` (code, durée minimale, durée par défaut, comptage, purge).

| Catégorie | Date de référence | Minimum | Défaut |
|---|---|---|---|
| Fichiers LIS (`LIS`) | journée du fichier (cascade : enregistrements, transactions) | 10 ans | 10 ans |
| Journaux GAB (`JOURNAUX_GAB`) | dernière date du journal (cascade : sessions, opérations, passages gabiste) | 10 ans | 10 ans |
| Rapports VSS (`VSS`) | date de traitement | 10 ans | 10 ans |
| Chargebacks archivés (`CHARGEBACKS`) | journée du chargeback ; dossiers non archivés jamais purgés | 10 ans | 10 ans |
| Écarts de rapprochement (`ECARTS_RAPPROCHEMENT`) | date du traitement | 10 ans | 10 ans |
| Réclamations closes (`RECLAMATIONS`) | date de clôture ; réclamations non closes jamais purgées | 5 ans | 10 ans |
| Piste d'audit (`AUDIT`) | date de l'événement | 5 ans | 10 ans |
| Historique des traitements automatiques | date d'exécution | 3 mois | 2 ans |

Minimum de 10 ans pour les opérations : obligation de conservation des enregistrements de transactions (LBC/FT),
**à confirmer avec la conformité de la banque**.

- Durées : paramètres `conservation.<code>.mois` (créés à la première modification), motif obligatoire, historique
  `parametre_historique`, audit `CONSERVATION`.
- Aperçu : nombre d'éléments à purger par catégorie, plus ancienne donnée.
- Purge manuelle : motif + saisie du mot PURGER ; chaque catégorie dans sa propre transaction (une erreur n'arrête pas
  les autres) ; détail dans `purge_execution`, audit `PURGE`.
- Purge automatique (désactivée par défaut) : `conservation.purge.planifiee`, `conservation.purge.jour` (1 à 28),
  vérifiée chaque nuit à 02:30, une fois par mois (`PlanificationPurge`).
- Les fichiers d'origine archivés par les traitements automatiques (dossiers `donnees/`) ne sont pas purgés.
- Les traitements de compense et les appros / arrêtés GAB (pièces comptables) ne sont pas purgés.
