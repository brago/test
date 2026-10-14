# Correspondance des communes (décision 9)

> **Mise à jour du 15/10/2026 : relue, validée et appliquée.** La version validée est dans
> `database/data/communes/` ; elle est produite par `appliquer_decisions.py` à partir de la proposition ci-dessous,
> qui reste comme trace de la relecture.

**Historique — proposition initiale.** Les données sources ne sont pas modifiées. Après votre relecture, ce fichier
alimentera une table administrable ; le filtre de l'API utilisera alors les communes officielles,
et l'affichage gardera le libellé d'origine.

## Fichiers

| Fichier | Contenu |
|---|---|
| `COMMUNES_OFFICIELLES_INSEE_2026.csv` | Les 34 communes officielles et leur code INSEE (Code officiel géographique INSEE au 1er janvier 2026). |
| `COMMUNES_CORRESPONDANCE_PROPOSITION.csv` | Une ligne par valeur brute de `commune` trouvée dans `01_FICHES_APP` (78 valeurs, 458 fiches). |
| `generer_proposition.py` | Script qui produit la proposition (règles automatiques + cas tranchés à la main). |

Format : UTF-8, séparateur `;` (s'ouvre directement dans Excel). Les listes sont séparées par ` | `.

## Colonnes

| Colonne | Sens |
|---|---|
| `valeur_brute` | Valeur exacte de la source (clé de correspondance). |
| `type` | `EXACT` (nom officiel), `VARIANTE` (orthographe, article, apostrophe), `LIEU_DIT` (commune + précision), `MULTI` (plusieurs communes), `ZONE` (aucune commune, zone seulement), `COMMUNE_ZONE`, `SANS_COMMUNE`. |
| `communes_officielles` / `codes_insee` | Commune(s) officielle(s) retenue(s) pour le filtre. |
| `lieu_dit` | Précision conservée telle qu'écrite (ex. « Anse Mitan »). |
| `zone` | Zone proposée. Vocabulaire utilisé : Nord, Nord Caraïbe, Centre, Sud, Sud Caraïbe, Île entière, Activité mobile. |
| `a_valider` | `OUI` pour les 14 lignes tranchées à la main ou incertaines. |
| `nb_fiches` / `nb_fiches_actives` | Fiches concernées (toutes / actives). |
| `codes_postaux_observes` | Codes postaux saisis sur ces fiches (aide à la relecture ; les codes postaux ne sont pas les codes INSEE). |
| `exemples_ids` | Jusqu'à 5 fiches concernées. |

## Points d'attention

- **Gros-Morne** : le nom officiel INSEE est « Gros-Morne », sans article ; « Le Gros-Morne » est classé en variante.
- **Caravelle** est rattachée à La Trinité (presqu'île) ; à confirmer pour `Anses-d'Arlet / Caravelle` (ACT-064).
- **Nord Caraïbe** (NAT-020) et **Nord de l'île** (NAT-021) portent un code postal de commune (97226, 97260) : faut-il aussi rattacher la commune ?
- **Pitons du Carbet**, **À localiser**, **(vide)** : aucune commune déduite (fiches inactives).
- **Saint-Esprit** n'a aucune fiche dans le catalogue.

Corrigez directement les colonnes `communes_officielles`, `lieu_dit` et `zone` ; les colonnes de comptage sont informatives.
