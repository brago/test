# Pilotage et réglementaire (lot 8)

Source : guide MONETICA V2 § VII (« KPI – Performance en volume » en graphique et en tableau croisé,
« Reporting réglementaire » BCEAO / VISA par trimestre). Le guide ne montre que les écrans de sélection :
les formats des états sont à fournir.

## Indicateurs (menu `REPORTING_KPI`)

Période : du mois … au mois … (36 mois au plus). Mesure : montant (XOF) ou nombre d'opérations.

| Axe | Indicateur | Source |
|---|---|---|
| GAB | Retraits distribués par nos GAB (toutes cartes) | Journaux électroniques, retraits dont les billets ont été pris |
| GAB | Retraits de cartes d'autres banques sur nos GAB | LIS, avis d'acquisition TC 49, code commerçant 6011 |
| TPE | Achats de cartes d'autres banques sur nos TPE | LIS, TC 49, autres codes commerçant |
| Carte | Retraits de nos cartes sur les GAB d'autres banques | LIS du switch (SW), TC 07 première présentation, par BIN |
| Carte | Achats de nos cartes chez les commerçants | LIS du switch, TC 05 première présentation, par BIN |
| Carte | Retraits de nos cartes sur nos GAB (on-us) | Journaux électroniques, cartes des BIN déclarés |

- Date retenue : date de la transaction (à défaut, journée du fichier) ; montant : `DESTINATION AMOUNT`.
- Axe carte : restreint aux BIN déclarés (*Paramétrage › GAB, profils et BIN*) dès qu'il y en a ; libellé = produit du BIN.
- Écran : chiffres clés (montant, opérations, entités actives, montant moyen), histogramme mensuel,
  classement des 10 premiers, tableau croisé entités × mois ; export Excel / PDF du tableau croisé.

## Réglementaire (menu `REPORTING_REGLEMENTAIRE`)

Base trimestrielle, par réseau (GIM, VISA, MASTERCARD selon le sous-fichier LIS) :

- Émission : retraits de nos cartes chez d'autres banques, achats, retraits on-us (journaux).
- Acquisition : retraits de cartes d'autres banques sur nos GAB, achats sur nos TPE, retraits distribués
  par nos GAB toutes cartes ; nombre de GAB / TPE actifs.

Export Excel / PDF. **Les formats officiels BCEAO (BP4) et VISA (Issuing, Acquiring, Prepaid) seront
produits à réception des modèles.**

## En attente

1. Modèles des états BCEAO BP4 et VISA (QOC Issuing / Acquiring / Prepaid).
2. « PNB sur transactions » (rentabilité) : règles de calcul ; unité des commissions d'interchange du TC 49 à confirmer.
3. Nombre de cartes en circulation, comptes, TPE installés : référentiels à fournir (ou extraction Amplitude).
