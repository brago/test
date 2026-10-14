# Palette des groupes carte — proposition à valider

> Statut : **PROPOSITION**. Rien n'est saisi dans le back-office.
> Vérification : `python3 docs/plans/palette/verifier_palette.py` (depuis `ekotrip-backend/`).

## Proposition

Il y a **7 groupes carte**, et non 8 : depuis la décision 5, « Professionnels » est rattaché au groupe Bouger.

| Groupe (slug) | Catégories | Couleur | Inspiration charte | Icône (clé API) |
|---|---|---|---|---|
| Dormir (`dormir`) | Hébergements | `#0679AC` | bleu lagon | `bed` |
| Manger local (`manger-local`) | Gastronomie | `#CC3711` | corail | `utensils` |
| Nature (`nature`) | Nature | `#3E7446` | vert sauge / eucalyptus | `tree` |
| Découvrir (`decouvrir`) | Patrimoine & découverte | `#644321` | sable, en version foncée (ocre brun) | `landmark` |
| Bouger (`bouger`) | Activités, Professionnels | `#284DB5` | bleu océan profond | `person-hiking` |
| Bien-être (`bien-etre`) | Spa & bien-être | `#93296F` | prune / orchidée | `spa` |
| Artisanat (`artisanat`) | Artisanat & savoir-faire | `#4E6155` | gris-vert vétiver | `hand` |
| *Regroupement (cluster)* | — | `#2F3B45` + nombre en blanc | ardoise neutre | — |

**Rendu proposé d'un repère** : un disque de la couleur du groupe, une icône blanche et un liseré blanc de 2 px. Le liseré détache le repère de n'importe quel fond (terre, végétation, eau, routes). Le repère sélectionné est plus grand et a un liseré plus épais : sa forme change, pas seulement sa couleur.

**Beige sable** (charte) : trop clair pour porter une icône blanche, il sert aux **surfaces** (fond des cartes de liste, de la mini-fiche, des filtres), pas aux repères.

## Contrôles

| Contrôle | Résultat | Seuil |
|---|---|---|
| Contraste icône blanche / couleur du repère | 4,8:1 à 8,9:1 (regroupement 11,5:1) | ≥ 4,5:1 (WCAG AA texte) |
| Contraste repère / fond de carte clair | 4,2:1 à 7,7:1 | ≥ 3:1 (WCAG élément graphique) |
| Contraste repère / eau | 3,1:1 à 5,6:1 | ≥ 3:1 |
| Écart minimal entre deux groupes, vision normale | ΔE2000 13,4 (Nature / Artisanat) | ≥ 10 |
| Protanopie (simulation) | ΔE2000 10,0 (Manger local / Découvrir) | ≥ 10 |
| Deutéranopie | ΔE2000 10,1 (Nature / Découvrir) | ≥ 10 |
| Tritanopie | ΔE2000 9,9 (Dormir / Nature) | ≥ 10 (limite) |

Méthode : contraste WCAG 2.x, distance CIEDE2000, simulation de Machado et al. (2009) à sévérité maximale. Les couleurs de fond (terre `#F2EFE9`, végétation `#DDE8CF`, eau `#A8D5E5`) approchent un fond clair de type Mapbox Streets ; il faudra les revérifier sur le style choisi.

## Limites, en toute transparence

- Avec 7 catégories, **aucune palette ne reste parfaitement distincte pour tous les types de daltonisme**. Un écart d'environ 10 se perçoit nettement côte à côte, moins bien sur deux repères isolés et éloignés. Voilà pourquoi **l'icône porte toujours l'information** (règle « jamais par la seule couleur ») ; la couleur ne fait que l'aider.
- Paires les plus proches : Dormir / Nature en tritanopie (une forme rare) et Manger local / Découvrir en protanopie. Leurs icônes (lit / arbre, couverts / monument) sont très différentes.
- Pour s'éloigner davantage du rouge, Découvrir a été foncé (ocre brun) plutôt que gardé en beige sable.
