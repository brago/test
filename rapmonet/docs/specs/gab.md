# GAB : paramétrage, approvisionnements, arrêtés (lot 5)

Source : guide utilisateur MONETICA V2, § V (écrans « Paramétrage GAB », « Profil GAB », « Bin cartes »,
« Approvisionnement GAB », « Arrêté GAB », rapports). Le PDF éditeur n'est pas versionné.

## Paramétrage (menu `PARAMETRAGE_GAB`)

| Écran | Contenu |
|---|---|
| GAB | Terminal ID (celui des journaux), libellé, localisation, agence, marque (NCR, WINCOR…), profil, comptes caisse / manquant / excédent, actif |
| Profils GAB | Cassettes A à D : coupure (XOF) et **type de billet NCR** (1 à 4) |
| BIN cartes | BIN (6 à 11 chiffres), libellé produit, type débit / crédit / prépayée |

Les compteurs du journal NCR (`NOTES PRESENTED`, `CASH TOTAL`, `ADD CASH`) sont donnés **par type de billet**,
pas par cassette (colonne TYPE du bloc `CASSETTE INFORMATION`). Plusieurs cassettes peuvent partager un type ;
un type a alors une seule coupure (contrôlé). Profil livré :

| Cassette | Type | Coupure |
|---|---|---|
| A | 1 | 10 000 |
| B | 1 | 10 000 |
| C | 2 | 5 000 |
| D | 3 | 2 000 |

Quand un GAB est paramétré, l'intégration des journaux valorise les billets avec les coupures de son profil
(interface `ingestion.CoupuresGab`, implémentée par le module GAB) ; sinon le paramètre global `ejournal.coupures`.

## Approvisionnement (menu `GAB_APPRO`)

- Saisie : GAB, date et heure, gabiste, référence, n° de bon de chargement, commentaire ; billetage = nombre
  de billets par cassette, la coupure vient du profil du GAB ; montant total calculé.
- **Contrôle avec le journal** : le passage du gabiste du même jour (ajout d'espèces, `ADD CASH`) donne les
  billets ajoutés par type ; la saisie, regroupée par type, est comparée → *Conforme*, *Écart* (détail par
  coupure) ou *Pas de journal*. Le contrôle est recalculé à chaque consultation (le journal arrive la nuit).
  Le bouton « Reprendre » recopie le journal pour les types portés par une seule cassette ; pour les cassettes
  qui partagent un type (A et B), le total du journal est affiché, à répartir.
- Référence sur 7 chiffres (séquence), statut `SAISI` → `VALIDE` ou `ANNULE`.

## Arrêté (menu `GAB_ARRETE`)

Reprend l'écran « Arrêté GAB » de MONETICA V2 :

| Rubrique | Source |
|---|---|
| (0) Appro arrêté | dernier appro **validé** non encore arrêté du GAB (un appro n'est arrêté qu'une fois) |
| (1) Billets en cassette, (2) rejets / retraits — par type | journal : premier relevé du passage superviseur (avant remise à zéro), compteurs `CASSETTE` et `REJECTED` ; modifiables |
| (3) Inventaire physique des cassettes, (4) du bac de rejet — par cassette | saisie |
| (5) = (1) + (2) | solde selon le journal |
| (6) = (3) + (4) | solde physique |
| (7) Transactions en attente | saisie |
| (8) Solde comptable du compte caisse | saisie, en attendant l'extraction Amplitude |
| Écart physique / journal | (6) − (5) ; négatif = manquant |
| Écart physique / comptable | (6) + (7) − (8) **(formule à confirmer)** ; à imputer au compte manquant ou excédent du GAB |

Amélioration par rapport à MONETICA V2 : **solde théorique** = (0) − retraits distribués selon les journaux
intégrés entre l'appro et l'arrêté, avec la **couverture** (jours avec journal / jours de la période) ;
le solde théorique n'est fiable que si tous les journaux de la période sont intégrés.

## Validation à quatre yeux (menu `GAB_VALIDATION`)

- Un appro ou un arrêté `SAISI` est validé par une **autre personne** que celle qui l'a saisi ou modifié
  (refusé sinon, y compris par l'API). Modification possible tant qu'il n'est pas validé.
- Annulation avec motif obligatoire ; un appro arrêté ne peut être annulé qu'après annulation de son arrêté.
- Écran « Validation » : appros et arrêtés en attente, avec le résultat du contrôle journal.
- Piste d'audit : `GAB_APPRO_SAISIE`, `GAB_APPRO_VALIDATION`, `GAB_APPRO_ANNULATION`, mêmes actions pour
  `GAB_ARRETE_*`, `PARAMETRAGE_GAB`.

## Rapports

- Rapport d'approvisionnement et rapport d'arrêté (PDF A4) : informations, billetage, contrôles, suivi de la
  validation, cadres de signature (gabiste, saisie, validation).
- Historiques des appros et des arrêtés : filtres (période, GAB, statut), export Excel / PDF.

## Réponses de la banque et points en attente

- **Origine de l'écart** (réponse utilisateur) : l'écart se constate entre le **solde de l'approvisionnement**
  et le **solde de l'arrêté** ; il peut provenir de la différence entre le montant trouvé à l'arrêté et le
  **solde du compte bancaire** (compte caisse du GAB). Cohérent avec les rubriques actuelles :
  appro (0) − retraits = solde attendu, comparé au montant trouvé (6) et au solde du compte (8).
- **Écran « Arrêté GAB » de référence : sera fourni par la banque.** Les formules provisoires ci-dessus
  ((6) − (5) et (6) + (7) − (8)) seront alignées sur cet écran ; ne pas les modifier avant de l'avoir reçu.
- Le solde du compte caisse (8) sera lu dans Amplitude dès réception des extractions.
- Écrans MONETICA V2 non repris à ce stade : « Arrêté GAB périodique », « Analyse compte GAB »,
  « Consultation encaisse GAB » (attente de leur description).
