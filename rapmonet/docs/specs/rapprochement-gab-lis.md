# Rapprochement GAB ↔ LIS (retraits des cartes confrères)

Premier rapprochement du lot 6, sans Amplitude. Menu *Rapprochements › GAB ↔ LIS* (`RAPPROCHEMENT_GAB_LIS`),
module `rapprochement` (`RapprochementGabLis`), sur `ingestion.ConsultationJournaux` (journaux NCR) et
`ingestion.RetraitsAcquis` (avis LIS).

## Principe

Un retrait d'une carte d'une autre banque distribué par un GAB de la banque est payé à la banque par le GIM :
il doit figurer dans un avis d'acquisition des fichiers LIS PR (TC 49, code commerçant 6011). Inversement, un avis
doit correspondre à un retrait réellement distribué.

- **Retraits du journal** : opérations RETRAIT au statut DISTRIBUE.
- **Cartes de la banque (on-us)** : compte client imprimé sur le ticket, ou carte d'un BIN déclaré. Elles ne passent
  pas par le GIM ; elles sont comptées à part et seront rapprochées avec Amplitude. Sur le GAB 10001001,
  318 retraits sur 322 des cartes 459847 portent un compte, aucune autre carte n'en porte : la banque a confirmé que
  les cartes 459847 sont les siennes. Le préfixe 459847 est découpé dans la table GIM en trois sous-plages
  ivoiriennes (45984700, 45984750, 45984780) : la sous-plage de la banque reste à préciser.

Les fichiers LIS « BACI » reçus (2023) viennent d'une **autre banque** : ils servent aux tests de lecture, pas à
déduire les BIN de la banque.
- **Avis LIS** : TC 49 code 6011 des fichiers PR, date de transaction dans la période (élargie d'un jour pour les
  retraits proches de minuit).

## Appariement

Même terminal, même carte (6 premiers + 4 derniers chiffres), même montant, et :
- heure du ticket et heure de l'avis à moins de la tolérance (`rapprochement.gab_lis.tolerance_minutes`, 120) ;
- ou, à un jour près, référence du ticket égale au code d'autorisation, au numéro de bordereau ou à la fin du RRN
  de l'avis (prioritaire).

**À confirmer sur des fichiers du même jour** : à quel champ LIS correspond la référence à 6 chiffres du ticket
(les journaux reçus datent de 2026, les LIS de 2023 ; aucun jour commun).

## Résultats

| Résultat | Règle | Écart à traiter |
|---|---|---|
| Rapproché | apparié | non |
| Retrait non remonté par le GIM | retrait sans avis, et LIS intégrés jusqu'à J + délai (`rapprochement.gab_lis.delai_lis_jours`, 3) | oui (manque à gagner à réclamer) |
| En attente des LIS | retrait sans avis, LIS pas encore intégrés jusqu'à J + délai | non |
| Avis LIS sans retrait au journal | avis sans retrait, alors que le journal du GAB couvre ce jour | oui |
| Journal du GAB absent | avis sans retrait, aucun journal intégré pour ce GAB ce jour | non (intégrer le journal) |

Les écarts se traitent à l'écran : *Justifié* ou *Régularisé* avec un commentaire obligatoire, ou retour à *À traiter*
(table `rapprochement_ecart`, clé stable de l'opération ; action d'audit `RAPPROCHEMENT_ECART`). Export Excel / PDF.

## Vérification

Test `RapprochementGabLisIntegrationTest` (journal anonymisé + fichier LIS synthétique). Démonstration en base de
développement : journal réel du GAB 10001001 et fichier LIS de démonstration fabriqué à partir de ses retraits
confrères (non versionné) : 14 rapprochés, 1 non remonté, 1 en attente, 1 avis sans retrait.
