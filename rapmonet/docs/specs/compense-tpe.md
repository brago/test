# Compense TPE

Source : classeur « TRAITEMENT COMPENSE TPE » (macro VBA `GenererFeuillesComptables`, non versionné : il contient
les comptes des commerçants). RAPMONET reprend ses règles à l'identique ; vérifié sur une journée réelle :
49 lignes de pièce sur 49 identiques (comptes, CCI, commission, TAF).

## Données

- **Achats** : avis d'acquisition LIS (TC 49) dont le code commerçant n'est pas 6011 (retraits GAB), d'une journée LIS.
- **Réseau** : indicateur de produit de paiement du TC 49 — 1 = VISA, 2 = Mastercard, sinon GIM UEMOA.
- **On-us** : achat GIM sans frais d'interchange (carte de la banque).
- **Référentiel commerçants** (*Compense TPE › Commerçants et comptes*) : terminal (complété à 8 chiffres),
  nom, compte à créditer, taux GIM / VISA / MCI. Import direct de la feuille « N° COMPTES » de la macro
  (xlsx ou xlsm) ; un taux supérieur à 1 ou suivi de « % » est lu comme un pourcentage.
- **Comptes généraux** (saisis à l'écran, figés dans chaque traitement) : EMIS ONUS, ACQ GIM, ACQ VISA, ACQ MCI,
  CMPNSE VISAMCI, CMPNSE GIM, CPTE COMM, CPTE TAF (COMM GIM pour mémoire, non utilisé par la macro).

## Calculs (par achat)

| Rubrique | Règle |
|---|---|
| Compte à débiter | VISA → ACQ VISA ; Mastercard → ACQ MCI ; GIM → ACQ GIM, ou EMIS ONUS si on-us |
| Commission TTC | montant × taux du commerçant pour le réseau (valeur exacte, avec décimales) |
| CCI = frais prélevés au commerçant | commission TTC arrondie à l'unité |
| COMM ACQUI (HT) | arrondi(commission TTC / (1 + taux TAF)), taux TAF 0,17 paramétrable |
| TAF | arrondi(commission TTC − COMM ACQUI) |

Arrondi « demi vers le haut » (fonction ARRONDI d'Excel). CCI = commission + TAF (vérifié par test).

Le franc CFA n'a pas de décimales : la CCI est affichée et exportée **arrondie à l'unité** (demande de la banque ;
la macro l'affichait avec décimales, ex. 217,5 → 218). Commission et TAF restent calculées sur la valeur exacte,
comme la macro : les calculer sur la CCI arrondie changerait 4 lignes sur 49 de la journée de référence
(1 F déplacé entre commission et TAF).

## Écritures (macro comptable, format d'import Amplitude)

Par achat, à la date de la transaction : débit compte réseau / crédit commerçant (montant) ; débit commerçant
(frais) ; crédit CPTE COMM (commission HT) ; crédit CPTE TAF (TAF). Puis, à J+1 de la journée :
compensation VISA-MCI → ACQ VISA, ACQ MCI ; compensation GIM → ACQ GIM, EMIS ONUS ; CPTE COMM → CMPNSE GIM
(commissions). Colonnes COMPTE, DATOPER, DATVAL (JJ/MM/AAAA), MNTFRF, MNTDEV, LIBELLE, CODOPSC (91000),
…, DEVISE (XOF), DATCPTA. La pièce est équilibrée (somme nulle, vérifiée). Les lignes de récapitulatif
de montant nul sont omises (la macro les écrivait à 0).

Libellés repris de la macro : « Paiement Transactions du 07 Mai Jeudi TPE <commerçant> REF <autorisation> »,
« Frais Cssions Transactions du … », « TAF Frais Cssions … », « ACTIVITE ACQUISITION ACHAT … DU … ».

## Traitement

1. *Préparer la compense* d'une journée LIS : aperçu par catégorie (VISA, MASTERCARD, GIM ACQ, GIM ONUS) et
   anomalies bloquantes (commerçant absent du référentiel ou désactivé, taux nul, comptes généraux manquants).
2. *Créer le traitement* (statut « À valider ») ; un achat n'est compensé qu'une fois.
3. *Validation à quatre yeux* par une autre personne (menu Validation) ; annulation motivée (les achats
   redeviennent compensables).
4. Téléchargement du classeur : feuilles PIECE GIM, PIECE VISA, PIECE MASTERCARD, MACRO, RÉCAP
   (brouillon possible avant validation).

## Différences avec la macro

- La macro ignore la **première ligne** de la feuille ACHAT ACQUISITION quand l'en-tête « CARTES » est absent
  (elle commence alors à la ligne 2) : un achat n'était pas payé au commerçant. RAPMONET le compense.
- Date du libellé : journée LIS du traitement (la macro prenait la date du premier achat de la feuille) ;
  récapitulatif daté de J+1 comme dans la macro.
- Plus de date d'expiration (la macro se fermait après le 31/10/2026).

## Confirmé par la banque

1. Le compte COMM GIM n'est utilisé par aucune écriture : c'est voulu (conservé pour mémoire dans les paramètres).
2. Toutes les commissions, y compris VISA / Mastercard, sont transférées vers CMPNSE GIM, comme dans la macro.
