# Journaux électroniques des GAB (NCR) – notes d'implémentation

Source : trois journaux réels d'un GAB NCR (non versionnés) et les explications de l'utilisateur.
Le test utilise un extrait anonymisé : `backend/src/test/resources/fichiers/ejournal/`.

## Format brut

- Fins de ligne : **retour chariot seul** (CR). Séquences de commande d'imprimante `ESC [ … ` à retirer.
- Nom du fichier : `<TerminalID>_<TerminalID>_<AAAAMMJJ>_<n>.jrn[.<copie>]`.
- Numéros de carte **déjà masqués par le GAB** (`400000______1234`). Par sécurité, une suite de 13 à 19
  chiffres à clé de Luhn valide serait masquée (sauf sur les lignes `ACCOUNT`).
- Date des tickets au format **AA/MM/JJ** (`26/05/22` = 22/05/2026, confirmé par les horodatages
  `5/22/2026 1:10:20 PM` du même journal). Paramètre `ejournal.ncr.format_date` (table `parametre_application`).

## Structure

| Élément | Repère |
|---|---|
| Session client | de `*TRANSACTION START*` à `TRANSACTION END` |
| Ticket de retrait | `CASH WITHRAWAL :<carte> NUM GAB :<terminal>` puis date/heure/référence, `AMOUNT :<montant> XOF`, `RESPONSE :<code>`, `ACCOUNT :<compte>` |
| Ticket de solde | `<carte>` puis date/heure/référence, `<code>  BALANCE INQUIRY`, `ACCOUNT :<compte>` |
| Billets | `NOTES PRESENTED a,b,c,d` (par type de cassette), `NOTES TAKEN` |
| Compteurs | bloc `CASH TOTAL` : DENOMINATION, DISPENSED, REJECTED, REMAINING par TYPE1…TYPE4 |
| Mode superviseur | de `SUPERVISOR MODE ENTRY` à `SUPERVISOR MODE EXIT` : `CASH COUNTS CLEARED` (arrêté), `CASH ADDED` + `TYPE n = …` (ajout d'espèces), compteurs CASSETTE/REJECTED/REMAINING/DISPENSED/TOTAL |

Une session peut contenir **plusieurs opérations** (solde puis retrait) ou **aucune** (PIN erroné,
délai dépassé, annulation) ; les sessions sans opération sont conservées avec leur extrait.

## Statut d'une opération

| Statut | Règle |
|---|---|
| DISTRIBUE | retrait, réponse 00, billets présentés et pris |
| NON_PRIS | retrait, réponse 00, billets présentés mais pas pris |
| APPROUVE | réponse 00, sans détail des billets (demande de solde, ou journal sans ces lignes) |
| REFUSE | réponse ≠ 00 (libellé dans la table `code_reponse`) |
| INCONNU | pas de ligne RESPONSE |

Incidents relevés : annulation client, délai dépassé, pas d'ARPC, refus carte (GENAC 2 AAC),
carte capturée, billets retirés.

## Contrôles

| Contrôle | Gravité |
|---|---|
| Fichier déjà intégré (SHA-256) | Bloquant |
| Ni session ni passage superviseur | Bloquant |
| GAB introuvable (ni NUM GAB, ni nom de fichier) | Bloquant |
| Session sans `TRANSACTION END` | Avertissement |
| Ticket sans date ou date illisible | Avertissement |
| Dates à plus d'un an de la date du nom de fichier (format de date à vérifier) | Avertissement |

Chaque fichier est intégré indépendamment. Une opération déjà présente (même GAB, type, carte,
date/heure, référence, montant, réponse) n'est pas enregistrée deux fois : les journaux qui se
chevauchent sont acceptés et le nombre de doublons est affiché.

## Intégration automatique (batch)

Chaque jour à **04:00** (paramétrable), RAPMONET intègre tous les journaux déposés dans le dossier
d'entrée (sous-dossiers acceptés, par exemple un par GAB). Écran : *Chargement des données › Journaux GAB*,
carte « Traitement automatique » (paramétrage, lancement immédiat, historique avec le détail par fichier).

| Paramètre (`parametre_application`) | Défaut | Rôle |
|---|---|---|
| `ejournal.batch.actif` | `true` | Exécution quotidienne activée |
| `ejournal.batch.heure` | `04:00` | Heure d'exécution (fuseau du serveur, ou `rapmonet.batch.fuseau-horaire`) |
| `ejournal.batch.repertoire_entree` | `donnees/ejournal/entree` | Dépôt des journaux par les GAB |
| `ejournal.batch.repertoire_archive` | `donnees/ejournal/archive` | Journaux intégrés ou déjà intégrés, rangés `<GAB>/<AAAA-MM>/` |
| `ejournal.batch.repertoire_rejet` | `donnees/ejournal/rejet` | Journaux refusés, rangés `<date>/`, avec `<fichier>.erreur.txt` |

Règles :

- **Fichier en cours de copie** : un fichier modifié depuis moins de 2 minutes
  (`rapmonet.batch.delai-stabilite-secondes`) est laissé pour le passage suivant. Les fichiers cachés (`.xxx`) sont ignorés.
- **Rattrapage** : si le serveur était arrêté à l'heure prévue, le traitement part à son redémarrage
  (jusqu'à 6 heures après). Une exécution manuelle ne remplace pas celle du jour.
- **Une seule exécution à la fois**, même avec plusieurs serveurs (index unique sur les exécutions « EN_COURS ») ;
  une exécution restée « EN_COURS » plus de 6 heures (arrêt brutal) est marquée « INTERROMPU ».
- Un fichier déjà intégré est archivé sans rien enregistrer ; un fichier du même nom déjà présent
  dans l'archive n'est jamais écrasé (suffixe `.1`, `.2`…).
- En cas d'erreur technique sur un fichier (droits, disque…), il reste dans le dossier d'entrée et sera repris.
- Historique : table `batch_execution` (compteurs + détail par fichier) ; piste d'audit `BATCH_EJOURNAL`
  (utilisateur `BATCH` pour l'exécution planifiée).
- Désactiver la planification sur un serveur (ex. second nœud) : `rapmonet.batch.planification-active: false`.

## Point ouvert

Sur les journaux reçus, `NOTES PRESENTED` × coupures (`DENOMINATION 5 10 20 50`) ne correspond au montant
du ticket que pour 4 retraits sur 308 (ex. 300 000 XOF pour 23,12,5,0 billets). La configuration des
cassettes du GAB (`CASSETTE INFORMATION` : deux cassettes de type 1, devise GBP) laisse penser à un GAB
de test. Ce contrôle n'est donc pas activé ; à confirmer sur un journal de production.
