# Phase 1 — Socle : plan proposé (à valider)

> Statut : **EN COURS** — décision du 14/10/2026 : **MySQL 8** retenu à la place de PostgreSQL (maintenance). Questions 2 à 8 (§10) toujours ouvertes : les choix provisoires sont isolés en configuration / tables pour être modifiés sans refonte.
> Périmètre (`CLAUDE.md`, `60_ROADMAP_DEV`) : schéma, import du catalogue avec rapport, API catalogue / carte / recherche, back-office de contrôle.
> Sources lues : classeur (`00`, `01`, `CATEGORIES`, `02`–`06`, `08`, `30`, `62`, `63`, `77`), dictionnaires CSV, JSON/GeoJSON, Spécifications V1.2, START HERE, Addendum, Lots 1.1 / 1.4 / 1.5 / 1.6, consigne carte Explorer, Recette globale, Données avant production.

---

## 1. Ce que disent les données (état constaté)

| Élément | Constat |
|---|---|
| `01_FICHES_APP` | 458 lignes, 458 IDs uniques. **383 actives** (= exactement les 383 du JSON), 75 inactives (`HORS_CATALOGUE`). |
| Catégories | 8 catégories, une seule catégorie par fiche dans la source (aucune multi-catégorie encore saisie). |
| Carte | 361 fiches `show_on_map = TRUE`, toutes avec coordonnées dans l'emprise Martinique ; GeoJSON = 361 points. |
| `geo_display_type` (actives) | INDICATIVE 155 · ACCESS_OR_DEPARTURE 124 · EXACT 78 · NON_NAVIGABLE 26. |
| `precision_geo` | ~60 libellés libres différents ; la classe dérivée n'existe que dans le JSON (pas dans le classeur). |
| Badge éco | `eco_badge_public` = NULL partout. 85 A/A+ legacy, 62 pré-évaluations → aucun badge public. |
| Budget | Renseigné pour 5 fiches seulement (378 NULL). |
| Circuits | 4 circuits, 84 étapes, 21 notes (12 précautions, 9 options d'hébergement). |
| POI / relations | `02` : 16 lignes, `03` : 9 lignes, `04_PRO_ACTIVITES` : 65 liens pro ↔ activité. |
| Médias | `06_MEDIAS` : en-têtes seulement, **0 média**. |
| Réservation | `reservable_ekotrip = FALSE` partout, `url_reservation` vide. |

---

## 2. Stack et choix techniques

- **Laravel 11, PHP 8.3** (disponible dans l'environnement), projet créé à la racine de `ekotrip-backend/`.
- **MySQL 8** (décision projet) : jeu de caractères `utf8mb4`, collation **`utf8mb4_0900_ai_ci`** (insensible aux accents et à la casse) pour toutes les tables ; **index FULLTEXT** (InnoDB, parser par défaut) sur les champs de recherche. Proximité / bbox en SQL simple (383 points, distance haversine calculée en requête) : pas de type spatial en phase 1.
- **Tolérance aux fautes** (suggestions) : la liste des noms et communes publics est mise en cache **Redis** ; la comparaison de proximité (normalisation sans accents + distance de Levenshtein / similarité) est faite **en PHP**, ce qui suffit pour 383 fiches. Toute la recherche passe par une interface `SearchEngine` (implémentation `MysqlSearchEngine`) : un futur `MeilisearchEngine` la remplacera sans toucher à l'API.
- **Laravel Sail** pour le développement local : services `mysql` (8.0) et `redis`.
- **Redis** : cache des réponses carte et catalogue, invalidé à chaque import ou modification en back-office.
- **Filament 3** pour le back-office (comptes admin uniquement en phase 1).
- **Pest** pour les tests, sur une vraie base MySQL 8 (`testing`) afin de couvrir FULLTEXT et la collation ; Redis réel pour le cache.
- Lecture du `.xlsx` via **OpenSpout** (streaming, léger).
- Pas d'appel Mapbox côté backend en phase 1 : le rendu de carte est côté mobile. Matrix et Directions viendront avec Mon Ékotrip.

---

## 3. Schéma de base de données

Clé primaire des fiches = **l'`id` du catalogue** (`HEB-001`…), en `varchar` : stable, jamais régénéré.

| Table | Contenu | Remarques |
|---|---|---|
| `map_groups` | Groupes carte (Dormir, Manger local, Nature, Découvrir, Bouger, Bien-être, Artisanat) : libellé, icône, couleur, ordre | Administrable (Lot 1.4 §4). |
| `categories` | Les 8 catégories de `CATEGORIES` + `map_group_id` | Correspondance catégorie → groupe modifiable sans code. |
| `fiches` | Tous les champs de `01_FICHES_APP` + extensions `62` (`accroche_courte`, `date/source/statut_derniere_verification`) + champs dérivés (`geo_display_type`, `geo_display_label`, `geo_navigation_allowed`, `eco_badge_public`, `budget_data_status`, `publication_status`) | Champs internes (`score_ekotrip`, `niveau_ekotrip`, `statut_evaluation`, `source_*`, `id_activites_associees`…) conservés mais **jamais sérialisés** dans l'API publique. |
| `fiche_category` | Pivot fiche ↔ catégorie, `is_primary` | Initialisé avec la catégorie principale ; multi-catégories ajoutables en back-office. |
| `precision_geo_rules` | Libellé `precision_geo` → `geo_display_type` | Initialisée depuis les couples observés dans le JSON ; un libellé inconnu → `NON_NAVIGABLE` + ligne au rapport. |
| `fiche_pois` | Sous-points de `02_PATRIMOINE_POI` et `03_ACTIVITES_POI` | Usage interne / affichage fiche ; **aucun marqueur** généré. |
| `pro_activity_links` | `04_PRO_ACTIVITES` | Seuls les liens `Afficher dans app = TRUE` entre fiches publiques sont exposés. |
| `circuits`, `circuit_steps`, `circuit_notes` | `05A`, `05_CIRCUITS`, `05D` | Une étape référence l'ID de la fiche maître ; aucune distance ni durée inventée. |
| `media` | Schéma générique `06_MEDIAS` (`target_type` FICHE / CIRCUIT / EVENT, `source_type`, `rights_status`, `publication_status`…) | Vide au départ ; seul `ACTIVE` sera servi. |
| `eco_evaluations`, `eco_proofs` | Évaluation + preuves datées, validables / révocables (Lot 1.1 §5) | `eco_badge_public` n'est calculé **que** depuis une évaluation `VALIDATED` avec preuve. |
| `fiche_reports` | Signalements (`62` : motif, commentaire, statut NOUVEAU → TRAITÉ / REJETÉ) | Ne modifie jamais la fiche. |
| `import_runs`, `import_issues` | Historique et rapport de chaque import | Consultable en back-office. |
| `settings` | Paramètres administrables (rayon « Autour de moi », etc.) | Accueillera aussi plus tard les montants commerciaux (jamais en dur). |

La table `events` n'est **pas** créée en phase 1 (phase 3), mais `media.target_type` prévoit déjà `EVENT`.

---

## 4. Import du catalogue

Commande `php artisan ekotrip:import [--dry-run]` :

1. Lit `docs/data/EKOTRIP_FINAL.xlsx`, la source canonique, y compris les 75 fiches inactives, importées comme **non publiques** pour rester contrôlables en back-office (Lot 1.1 : « fiche rouge présente en archive mais non publiable »).
2. Upsert **par `id` uniquement**, sans aucune déduplication sur téléphone, site ou adresse ; idempotent, donc relançable.
3. Calcule les champs dérivés (`geo_display_*`, `publication_status`, `budget_data_status`, `eco_badge_public = NULL`).
4. Neutralise les placeholders (« À CONFIRMER » → `NULL` côté public, valeur brute conservée en interne).
5. Importe catégories, pivot, POI, liens pro, circuits, étapes et notes.
6. **Contrôle croisé** avec `EKOTRIP_CATALOGUE.json` et `EKOTRIP_CARTE.geojson` : même ensemble d'IDs actifs, mêmes classes géo, mêmes 361 points. Tout écart est signalé.
7. Produit un **rapport** (console + back-office) : totaux importés / mis à jour / rejetés, fiches sans coordonnées, placeholders neutralisés, références cassées, étapes de circuit vers une fiche non publique, libellés `precision_geo` inconnus.

Si une ligne est en erreur, elle est rejetée et l'import continue : jamais de valeur inventée pour « réparer ».

---

## 5. API publique v1 (lecture seule, sans authentification)

Règle commune à tous les endpoints : uniquement les fiches **publiques** (`is_active` + statut publiable) ; `NULL` reste `null` ; aucun champ interne.

| Méthode | Endpoint | Rôle |
|---|---|---|
| GET | `/api/v1/config` | Groupes carte, catégories, rayon par défaut, libellés des classes géo. |
| GET | `/api/v1/categories` | Catégories + groupe + nombre de fiches publiques. |
| GET | `/api/v1/communes` | Communes ayant au moins une fiche publique. |
| GET | `/api/v1/fiches` | Liste paginée. Filtres : `q`, `categories[]`, `groups[]`, `communes[]`, `near=lat,lng` + `radius`, `bbox`, `eco_validated`. Inclut les fiches sans coordonnées. |
| GET | `/api/v1/fiches/{id}` | Fiche détaillée : actions autorisées (`can_navigate`, `can_call`, `has_website`, `can_book = false`), POI, liens pro, circuits liés, médias résolus. 404 si la fiche n'est pas publique. |
| GET | `/api/v1/map/fiches` | **GeoJSON** allégé pour la carte : mêmes filtres + `bbox` ; uniquement `show_on_map` + coordonnées valides ; propriétés de la mini-fiche (id, nom, commune, catégorie, groupe, classe et libellé géo, `navigation_allowed`, média principal, badge si validé). |
| GET | `/api/v1/search/suggest` | Suggestions après quelques caractères (nom > commune > catégorie / tags > description). |
| GET | `/api/v1/circuits`, `/api/v1/circuits/{id}` | Circuits avec étapes publiques et notes de précaution, sans km ni durée inventés. |
| POST | `/api/v1/fiches/{id}/reports` | Signalement d'une information incorrecte, limité en débit (anti-spam serveur). |

**Cohérence carte / liste** (Lot 1.5) : `/fiches` et `/map/fiches` partagent le même service de filtrage ; la liste contient en plus les fiches non cartographiables.

**Recherche** (`SearchEngine`) : index FULLTEXT sur `nom`, `commune`, `tags_ia`, `description` (+ libellé de catégorie dénormalisé `search_categories`) ; comparaisons `LIKE` sur colonnes en `utf8mb4_0900_ai_ci` pour les correspondances partielles de nom / commune ; score calculé selon le classement du Lot 1.5 §8 (nom exact > nom partiel > commune > catégorie / tags > description). Aucun boost lié à un partenaire.

**Suggestions** (`/search/suggest`) : préfixe / sous-chaîne d'abord, puis, si peu de résultats, tolérance aux fautes en PHP sur la liste noms + communes du cache Redis (invalidée à chaque import ou modification).

---

## 6. Back-office de contrôle (Filament)

- **Fiches** : recherche par ID, nom ou commune ; affichage des éléments du Lot 1.1 §10 (ID, nom, commune, catégories, statuts, lat / lng, téléphone, site, sources, niveau et preuves éco) ; filtres « publique », « sur la carte », « sans coordonnées », classe géo.
- Modifier lat / lng, retirer une fiche de la carte sans la supprimer, gérer les catégories associées (Lot 1.4 §16).
- Correspondances catégorie → groupe carte et `precision_geo` → classe géo.
- Évaluations et preuves éco : saisie, validation, révocation.
- Circuits (consultation), signalements (file NOUVEAU / EN_COURS / TRAITÉ / REJETÉ), rapports d'import.
- Accès réservé aux administrateurs (policies Laravel).

---

## 7. Tests (Pest)

Un test par règle non négociable touchée par la phase 1, plus les recettes des Lots 1.1, 1.4, 1.5, 1.6 et de l'Addendum :

- Import : 458 IDs conservés, aucun doublon ; ré-import idempotent ; aucune coordonnée créée pour une fiche sans GPS ; placeholders jamais exposés ; rapport complet.
- Publication : une fiche inactive, archivée ou « NE PAS PUBLIER » est absente de la liste, de la carte, de la recherche et de la fiche (404).
- Carte : seulement `show_on_map` + coordonnées valides ; les 4 classes géo ; `NON_NAVIGABLE` → `navigation_allowed = false`.
- Badge : `eco_badge_public` NULL malgré A/A+ ou une pré-évaluation ; badge présent seulement si l'évaluation est validée avec preuve ; un plan payant ne change rien.
- Médias : `TO_VERIFY` / `BLOCKED` / `DRAFT` jamais rendus.
- Recherche : « Trois-Îlets », « randonnée », « spa », « artisanat », « distillerie » ; accents et casse ; filtres cumulés ; carte et liste cohérentes.
- API : aucun champ interne sérialisé ; `null` conservé.
- Circuits : chaque étape publique pointe vers la bonne fiche maître ; une étape vers une fiche non publique n'est pas servie.
- Signalement : ID inexistant refusé ; la fiche n'est pas modifiée ; limitation de débit.

---

## 8. Découpage en commits

1. Squelette Laravel + Sail (MySQL 8, Redis) + Pest (`composer test`).
2. Migrations et modèles (§3).
3. Commande d'import + rapport + tests d'import.
4. Services de filtrage / recherche + API v1 + tests.
5. Back-office Filament + policies.
6. Recette phase 1 : exécution de la checklist et rapport.

---

## 9. Incohérences relevées dans les données (à arbitrer, rien n'a été corrigé)

1. **4 fiches `show_on_map = TRUE` mais `NON_NAVIGABLE`** (`precision_geo` = « À déterminer ») : GAS-003 Le Petibonum, GAS-009 Le Bambou (restaurant), GAS-013 Créole Arts Café, GAS-016 Le Point de Vue. Selon `63` : « pas de CTA vers un faux point ; masqué ou désactivé ».
2. **Placeholders exposables** : `interieur_exterieur = « À CONFIRMER »` (7 fiches), `niveau_activite = « À CONFIRMER »` (14 fiches), alors que la recette globale interdit d'exposer « À_CONFIRMIR ».
3. **Circuit 4, Jour 2** : l'étape « Habitation Beauséjour » pointe vers **HEB-019**, une fiche inactive (« NE PAS PUBLIER »). L'étape est marquée « REMPLACER / RETIRER », alors que `30_CIRCUITS_FINAL` annonce 84 étapes runtime « prêtes ».
4. **`04_PRO_ACTIVITES`** référence 6 fiches inactives (ACT-023, ACT-024, ACT-025, ACT-026, ACT-058, PRO-006).
5. **Statuts de publication** : le Lot 1.1 parle de `published / pending_review / unpublished / archived`, alors que la donnée utilise `PUBLIABLE`, `CONTENU ÉDITORIAL`, `À CONTRÔLER`, `À RESTRUCTURER`, `À RECLASSER`, `NE PAS PUBLIER`, `ARCHIVÉ`…
6. **Version** : `EKOTRIP_CATALOGUE.json` porte `version: V1.1` alors que le classeur est V1.2 (mêmes 383 fiches, donc sans impact constaté).
7. **Groupes carte** : la catégorie « Professionnels » (29 fiches) n'a pas de groupe dans la liste du Lot 1.4.

---

## 10. Questions à valider avant de coder

1. ~~Base de données~~ — **tranché : MySQL 8** (`utf8mb4_0900_ai_ci`, FULLTEXT, suggestions tolérantes en PHP + Redis, moteur remplaçable par Meilisearch).
2. **Statuts de publication** (incohérence 5) : je propose `PUBLIABLE` et `CONTENU ÉDITORIAL` (si actives) → `published` ; `À CONTRÔLER` → `pending_review` ; `À RESTRUCTURER` et `À RECLASSER` → `unpublished` ; `NE PAS PUBLIER`, `ARCHIVÉ` et `ARCHIVER…` → `archived`. D'accord ?
3. **Les 4 fiches « À déterminer »** (incohérence 1) : retirées de la carte (recommandé : pas de point fiable) ou affichées avec le libellé « Localisation non confirmée », sans bouton itinéraire ?
4. **Placeholders « À CONFIRMER »** (incohérence 2) : publiés comme `null` (« non renseigné »), avec la valeur brute conservée en interne ?
5. **Étape HEB-019** (incohérence 3) : importée mais jamais servie, et signalée au rapport ?
6. **« Professionnels »** (incohérence 7) : quel groupe carte, ou un groupe dédié ?
7. **Signalements** : à inclure dès la phase 1 (prévu « MVP » dans `62`) ou à reporter ?
8. **Hébergement cible** (S3, Redis, serveur) : connu dès maintenant, ou on reste en local et en test pour la phase 1 ?
