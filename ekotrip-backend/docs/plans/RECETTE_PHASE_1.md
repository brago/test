# Recette technique — Phase 1 (socle)

> Date : 14/10/2026 · Branche `claude/hello-m9ff11` · MySQL 8.0 / Redis · Laravel 13, Filament 5, Pest 4.
> Commande : `./vendor/bin/sail artisan test` (ou `php artisan test`) → **125 tests, 1 768 assertions, 0 échec**.
>
> Cette recette couvre le **backend** : données, API, back-office. Les critères purement mobiles
> (rendu Mapbox, clustering, retour à la carte avec le même zoom, permission de localisation)
> relèvent de l'application et sont marqués « App ».

## 1. Import du catalogue (Lot 1.1)

Rapport de `php artisan ekotrip:import` sur `EKOTRIP_FINAL.xlsx` :

| Compteur | Valeur | Attendu |
|---|---|---|
| Fiches lues / importées | 458 / 458 | 458 IDs uniques, conservés |
| Fiches actives = publiques | 383 | 383 (= JSON) |
| Statuts non publics | 39 à valider · 21 dépubliées · 15 archivées | 75 inactives, jamais exposées |
| Fiches sur la carte | **357** | 361 `show_on_map` − 4 « à géocoder » (décision 2) |
| Fiches publiques sans coordonnées | 19 | aucune coordonnée inventée |
| Écarts JSON (8 champs × 383 fiches) | **0** | 0 |
| Écarts GeoJSON (361 points) | **0** | 0 |
| Placeholders neutralisés | 35 valeurs / 21 fiches | jamais exposés |
| Circuits / étapes / notes | 4 / 84 (83 servies) / 21 | HEB-019 « à remplacer », jamais affichée |
| POI / liens pro ↔ activité | 25 / 65 | — |
| Ré-import | 458 fiches inchangées | idempotent |

| Contrôle (Lot 1.1 §9) | Résultat | Preuve |
|---|---|---|
| Unicité des ID | OK | `ImportTest` |
| Multi-catégories sans duplication | OK (pivot `category_fiche`) | `BackOfficeTest`, `ImportTest` |
| Statuts rouges jamais publics | OK | `PublicVisibilityTest` |
| Sources conservées et rattachées | OK (champs internes non exposés) | `ApiContractTest` |
| Circuits → fiches maîtres | OK | `ImportTest`, `CircuitTest` |
| GPS absent signalé, jamais inventé | OK | `ImportTest`, `MapTest` |
| Badge éco indépendant de « Vérifié » | OK | `EcoBadgeTest` |
| Rapport d'import | OK (console + back-office) | `ImportTest` |

## 2. Règles non négociables

| Règle (`CLAUDE.md`) | Statut | Tests |
|---|---|---|
| Ne rien inventer (GPS, prix, horaires, temps de trajet, badge…) ; NULL = non renseigné | OK | `ImportTest`, `ApiContractTest`, `MapTest`, `CircuitTest` |
| IDs stables, une fiche maître, multi-catégories par pivot | OK | `ImportTest` |
| Fiches non publiques absentes de l'API, de la recherche, de la carte et des suggestions | OK | `PublicVisibilityTest` |
| Carte : actives + `show_on_map` + coordonnées valides ; 4 classes ; NON_NAVIGABLE sans itinéraire | OK | `MapTest`, `GeoClassifierTest` |
| Badge éco : évaluation validée + preuve ; jamais A/A+ ni pré-évaluation ; plan commercial sans effet | OK | `EcoBadgeTest`, `EcoLevelTest`, `BackOfficeTest` |
| Médias : FICHE/CIRCUIT/EVENT, 4 sources, seul ACTIVE servi, Google Places jamais stocké | OK | `MediaTest` |
| Permissions côté serveur (policies) | OK | `BackOfficeTest` |
| Montants configurables | N/A en phase 1 (table `settings` prête) | — |
| Événements, Mon Ékotrip, réservation | Phases 2, 3 et 6 | — |

## 3. Explorer, recherche et fiches (Lots 1.4 / 1.5 / 1.6, Addendum)

| Critère | Statut | Preuve |
|---|---|---|
| Seules les fiches publiables avec coordonnées fiables sont sur la carte | OK | `MapTest` |
| Fiche rouge / archivée jamais visible | OK | `PublicVisibilityTest` |
| Mini-fiche / fiche détaillée : même ID | OK (API) · App | `ApiContractTest` |
| Recherche « Trois-Îlets », « randonnée », « spa », « artisanat », « distillerie » | OK | `SearchTest` |
| Accents et casse ignorés, fautes tolérées (suggestions) | OK | `SearchTest`, `SuggestionScoreTest` |
| Classement : nom > commune > catégorie/tags > description | OK | `SearchTest` |
| Filtres cumulables, carte et liste cohérentes | OK | `MapTest`, `SearchTest` |
| « Autour de moi » (rayon administrable), bbox | OK | `MapTest` |
| Actions masquées sans donnée, aucune réservation fictive | OK | `ApiContractTest` |
| Tester les 4 `geo_display_type` (Addendum) | OK | `MapTest` |
| Média BLOCKED / TO_VERIFY non rendu (Addendum) | OK | `MediaTest` |
| Absence de badge si `eco_badge_public` NULL (Addendum) | OK | `EcoBadgeTest` |
| Événement sans GPS / terminé (Addendum) | Phase 3 | — |
| Clustering, zoom, retour carte, refus de localisation | App | — |

## 4. Back-office (Lot 1.1 §10, Lot 1.4 §16)

| Critère | Statut |
|---|---|
| Rechercher une fiche et voir ID, nom, commune, catégories, statuts, lat/lng, téléphone, site, sources, niveau et preuves éco | OK |
| Modifier lat/lng (emprise Martinique contrôlée) ; voir l'éligibilité carte | OK |
| Retirer une fiche de la carte sans la supprimer | OK |
| Modifier le groupe cartographique d'une catégorie | OK |
| Contrôler les fiches sans coordonnées (filtre) | OK |
| Accès réservé aux administrateurs | OK |

## 5. Décisions de recette du 15/10/2026

Toutes testées dans `tests/Feature/Phase1DecisionsTest.php` (statuts, contenu éditorial hors carte, statut inconnu non public, fiches « à géocoder », HEB-019, groupe des Professionnels, rayon) et `tests/Feature/DatabaseServerTest.php` (version MySQL, collation).

Correction associée : dans le back-office, les filtres « Visibles du public » et « Sans coordonnées » n'agissaient pas (paramètre de fonction mal nommé pour Filament) ; corrigé et couvert par un test.

## 6. Points ouverts avant de clore la phase 1

1. **Communes** : correspondance appliquée (tests `CommuneMappingTest`). Reste à confirmer ACT-064 (provisoire) et à vérifier ACT-078 Mangofil (code postal 97228 / Les Trois-Îlets).
2. **Version MySQL de l'hébergeur** : à reporter dans `MYSQL_VERSION` et `EKOTRIP_MYSQL_MIN_VERSION`.
3. **Recette sur téléphone** des critères marqués « App ».
