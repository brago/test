# Chargebacks GIM (lot 7)

Source : guide MONETICA V2 § VI E (« Suivi CHARGEBACK EMISSION / ACQUISITION ») et analyse des fichiers
LIS réels (non versionnés).

## Données

Les chargebacks sont lus dans les fichiers LIS intégrés (sous-fichier GIM local). Toutes les opérations
d'un même litige portent le même **ARN** (`LIS_ACQUIRERS_REFERENCE_NUMBER`) :

| TC | Code usage | Étape |
|---|---|---|
| 05, 06, 07 | 1 | Première présentation (achat, crédit, retrait) |
| 15, 16, 17 | 1 | Chargeback |
| 05, 06, 07 | 2 | Représentation (ex. message « CASH TAKEN ») |
| 15, 16, 17 | 2 | Second chargeback |
| 25, 26, 27 | – | Redressement de la présentation |
| 35, 36, 37 | – | Redressement (annulation) du chargeback |

Informations reprises : carte (masquée), RRN, ARN, autorisation, date et heure de transaction, terminal,
commerçant / GAB, montant, devise, motif (`REASON CODE`), référence du chargeback, message.

## Émission ou acquisition

- **Émission** : la carte appartient à la banque (BIN déclarés dans *Paramétrage › GAB, profils et BIN*).
  La banque émettrice a fait le chargeback ; on suit la couverture et une éventuelle représentation de l'acquéreur.
- **Acquisition** : carte d'une autre banque, en général sur un GAB de la banque. Le chargeback est reçu ;
  la banque peut le représenter si les billets ont été pris.
- Sans BIN déclaré, tout est classé en acquisition (avertissement à l'écran). Les dossiers ouverts sont
  reclassés si les BIN changent.

## Dossier et cycle de vie

- Un dossier par ARN, créé automatiquement après chaque intégration LIS (événement `FichiersLisIntegres`)
  et à chaque consultation.
- Cycle affiché comme dans MONETICA V2 : première présentation (« Transaction non compensée » si elle
  n'est pas dans les fichiers intégrés), couverture / réception du chargeback, représentation, second
  chargeback, redressements.
- **Durée (jours)** depuis la journée LIS du chargeback ; **échéance** = journée + délai
  (`chargeback.delai_jours`, 45 par défaut, dans `parametre_application`). Après l'échéance sans
  représentation : « chargeback acquis » (émission) ou « délai dépassé, chargeback définitif » (acquisition).
- États : `EN_ATTENTE`, `REPRESENTE`, `SECOND_CHARGEBACK`, `ANNULE`, `DELAI_ECOULE`.

## Actions

- Recherche par période (journée du chargeback) ou par ARN / RRN ; filtre régularisation Non / Oui / Rejet / Tous ; archives.
- **Régulariser** : Oui (écriture passée) ou Rejet, avec commentaire obligatoire ; retour à « Non » possible tant que le dossier n'est pas archivé.
- **Archiver** les dossiers régularisés ou rejetés.
- **Visualiser journal GAB** (acquisition) : recherche du retrait dans les journaux intégrés
  (terminal, date ±1 jour, 4 derniers chiffres de la carte, montant) et conclusion :
  billets pris → représentation possible ; non pris ou refusé → chargeback fondé. L'extrait du journal est affiché.
- Export Excel / PDF (« Imprimer ») ; piste d'audit `CHARGEBACK_REGULARISATION`, `CHARGEBACK_ARCHIVAGE`.

## Points ouverts

1. Libellés des motifs GIM (ex. 0090) : table à fournir.
2. Délai réglementaire de représentation GIM à confirmer (45 jours par défaut).
3. Visa / Mastercard internationaux (fichiers VSS, IPM) : non couverts.
4. Saisie des chargebacks à émettre depuis RAPMONET : non prévue (ils sont émis dans le système GIM).
