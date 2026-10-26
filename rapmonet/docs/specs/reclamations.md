# Réclamations porteurs

Module `reclamation` : recherche transverse des opérations d'un porteur et registre des réclamations.
Menus *Réclamations porteurs › Recherche porteur* (`RECLAMATION_RECHERCHE`) et *Registre des réclamations*
(`RECLAMATION_SUIVI`).

## Recherche porteur (`RecherchePorteur`)

Critères : carte, compte, référence (ticket GAB, code d'autorisation, RRN, ARN), période (un an par défaut, deux au plus).

- **Carte** : numéro complet (13 à 19 chiffres, clé de Luhn contrôlée) ou 6 premiers + 4 derniers chiffres
  (`459847******1234`). Le numéro complet sert uniquement à calculer l'empreinte (`ingestion.OperationsPorteur.empreinte`)
  pour une correspondance exacte dans les LIS et les chargebacks ; il n'est ni conservé ni journalisé, et la recherche
  passe par POST (jamais dans une adresse). Les journaux GAB n'ont que la carte masquée : correspondance 6 + 4.
- **Compte** : tickets GAB qui l'impriment ; les cartes vues sur ces tickets (5 au plus) étendent la recherche aux LIS.
- **Résultats** :
  - opérations GAB (`ingestion.OperationsPorteur`) avec, pour chaque retrait distribué, le résultat du rapprochement
    GAB ↔ LIS (`rapprochement.StatutsRetraits` : rapproché, non remonté, en attente, carte de la banque…) et le
    traitement de l'écart ;
  - transactions LIS (journée jusqu'à 60 jours après la période, pour voir chargebacks et représentations) ;
  - chargebacks (`chargeback.DossiersPorteur`, avec situation du cycle) ;
  - réclamations déjà ouvertes pour la carte ou le compte.
- 300 opérations au plus par liste. Chaque recherche est tracée (`RECHERCHE_PORTEUR`, carte masquée).

## Registre (`ReclamationsService`)

- Ouverture depuis la recherche, avec les opérations cochées (instantané lisible conservé dans la réclamation) :
  client, contact, canal (guichet, téléphone, courrier, e-mail), objet, description, montant contesté.
  Numéro `REC-AAAA-NNNNN`, échéance = ouverture + `reclamation.delai_jours` (15 jours, écran Paramètres de
  l'application). Seule la carte masquée est enregistrée ; sans compte saisi, celui imprimé sur les tickets choisis.
- Cycle : Ouverte → En cours (prise en charge) → Close fondée / non fondée (conclusion communiquée au client,
  10 caractères au moins) ; réouverture motivée (nouvelle échéance). Commentaires de suivi. Chaque étape est tracée
  dans le suivi (`reclamation_suivi`) et l'audit (`RECLAMATION`).
- Fiche imprimable (navigateur) : informations, opérations concernées, suivi, zones de signature.
- Alertes : `RECLAMATIONS_EN_RETARD` (attention, échéance dépassée), `RECLAMATIONS_A_TRAITER` (info).
