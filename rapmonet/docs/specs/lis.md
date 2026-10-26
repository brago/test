# Fichier LIS du GIM – notes d'implémentation

Source : spécification GIM/HPS **LIS 3.038** (non versionnée, confidentielle) et analyse des
fichiers `LIS_IN_BACI_SW_23041_01` / `LIS_IN_BACI_PR_23041_01` (non versionnés : ils contiennent
des numéros de carte).

## Structure

- Enregistrements de 256 caractères : TC (1-2), séquence (3-8), numéro de TCR (9), données (10-256).
- Une transaction = un TCR 0 et les TCR suivants, jusqu'au prochain TCR 0.
- En-tête TC 90, fin TC 91. Cinq sous-fichiers dans cet ordre :

| Sous-fichier | En-tête / fin | Contenu |
|---|---|---|
| FC – commerçant | 92 / 93 | TC 70-77, 46 |
| FV – Visa | 94 / 95 | TC 05-37, 10, 20, 40, 48, 50-53, 46 |
| FM – Mastercard | 96 / 97 | TC 05-37, 10, 20, 50-53, 46 |
| FL – GIM local | 98 / 99 | TC 05-37, 09, 19, 10, 20, 49, 50-53, 46 |
| FS – switch | 80 / 81 | TC 55-68, 82 |

- Nom des fichiers : `LIS_IN_<adhérent>_AAJJJ_XX` (année, jour julien, séquence).

## Contrôles à l'intégration

| Contrôle | Gravité |
|---|---|
| En-tête TC 90 en première ligne, fin TC 91 en dernière | Bloquant |
| Séquence 3-8 = numéro de ligne | Bloquant |
| Ouverture / fermeture des sous-fichiers | Bloquant |
| TC 91 F.004 = nombre total de lignes | Bloquant |
| Fichier déjà intégré (empreinte SHA-256) | Bloquant |
| SW et PR de journées différentes | Bloquant |
| Fin de sous-fichier F.004 = nombre de lignes du sous-fichier | **Avertissement** : le GIM ne renseigne pas ce champ de façon fiable (ex. 12 annoncé pour un sous-fichier vide) |
| Longueur ≠ 256, TC/TCR inconnu, TC hors de son sous-fichier, valeur de champ invalide | Avertissement |
| Nom du fichier incohérent avec le type ou la date d'en-tête | Avertissement |

## Numéros de carte (PCI-DSS)

Les champs « CARDHOLDER CARD NUMBER » sont marqués `confidentiel` dans le paramétrage.
Avant tout stockage, le numéro est masqué (6 premiers + 4 derniers chiffres) **dans les
attributs et dans la ligne brute conservée**. Une empreinte HMAC-SHA256
(`<code>_EMPREINTE`, clé `RAPMONET_CLE_EMPREINTE_CARTE`) permet les rapprochements sans
numéro en clair.

## Reste à faire

- Structures non extraites automatiquement : TC 01, 02, 04, 41, 47, 55, 56, 57, 60, 61, 63, 67, 72.
- Variantes Visa du TC 46 (§7.50 à 7.59, sous-fichier FV) : dépendent de REPORT GROUP / SUBGROUP.
- Rapprochement LIS / core banking (lot 6) à partir des attributs ARN, RRN, autorisation, terminal, montant, date.

## Chargement automatique

- Les fichiers LIS déposés dans le dossier d'entrée (`lis.batch.repertoire_entree`, par défaut `donnees/lis/entree`) sont intégrés chaque jour à l'heure `lis.batch.heure` (08:00 par défaut), ou sur demande (« Lancer maintenant » sur l'écran de chargement LIS).
- Le type est lu dans le nom du fichier (`…_SW_<jjjjj>…` ou `…_PR_<jjjjj>…`) ; sans type, le fichier est rejeté. Chaque fichier est intégré seul, avec les mêmes contrôles que l'intégration manuelle.
- Fichier intégré ou déjà intégré : déplacé dans l'archive, classé par mois de journée (`aaaa-mm`). Fichier refusé : déplacé dans le dossier de rejet avec un fichier `.erreur.txt` donnant le motif.
- Historique dans `batch_execution` (traitement `LIS`), action d'audit `BATCH_LIS`. Une exécution en erreur ou avec rejets lève une alerte (menu « Fichiers LIS »).
- Mécanique commune avec le batch des journaux GAB : `ingestion.internal.BatchDossier`.
- Reste à faire : récupération directe sur le serveur SFTP du GIM (attente des accès).
