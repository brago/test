# ÉKO TRIP Martinique — backend

API REST de l'application mobile et back-office d'administration (Laravel 13, MySQL 8, Redis, Filament 5).
Règles métier et priorité documentaire : voir [`CLAUDE.md`](CLAUDE.md) et [`docs/`](docs/).

## Démarrage avec Sail

```bash
cp .env.example .env
composer install
./vendor/bin/sail up -d                 # MySQL 8 (utf8mb4_0900_ai_ci) + Redis
./vendor/bin/sail artisan key:generate
./vendor/bin/sail artisan migrate
./vendor/bin/sail artisan ekotrip:import          # import du classeur + rapport
./vendor/bin/sail artisan ekotrip:create-admin vous@exemple.fr
```

Back-office : `http://localhost/admin` · API : `http://localhost/api/v1/...`

**Version de MySQL** : l'image Sail suit `MYSQL_VERSION` (défaut 8.4). Dès que la version de l'hébergeur est connue,
la reporter dans `MYSQL_VERSION` et `EKOTRIP_MYSQL_MIN_VERSION` : `tests/Feature/DatabaseServerTest.php` vérifie
la version et la collation `utf8mb4_0900_ai_ci` du serveur de test.

**Installation des dépendances** : `composer install` sans option. Il suffit d'un accès normal à GitHub (archives des paquets).

## Commandes

| Commande | Rôle |
|---|---|
| `artisan ekotrip:import [--dry-run] [--details=ERROR\|WARNING\|INFO]` | Importe `docs/data/EKOTRIP_FINAL.xlsx` (idempotent), contrôle le résultat contre le JSON et le GeoJSON, affiche et enregistre le rapport. |
| `artisan ekotrip:create-admin <email>` | Crée ou promeut un administrateur. |
| `artisan test` | Suite Pest sur la base MySQL `testing` (créée par Sail). |

## API publique v1 (lecture seule)

| Endpoint | Rôle |
|---|---|
| `GET /api/v1/config` | Groupes carte, classes géo, rayon par défaut |
| `GET /api/v1/categories`, `/communes` | Référentiels et comptes |
| `GET /api/v1/fiches` | Liste ; filtres `q`, `categories[]`, `groups[]`, `communes[]`, `near=lat,lng`, `radius`, `bbox=minLng,minLat,maxLng,maxLat`, `eco_validated`, `per_page` |
| `GET /api/v1/fiches/{id}` | Fiche détaillée (404 si non publique) |
| `GET /api/v1/map/fiches` | GeoJSON des marqueurs (mêmes filtres) |
| `GET /api/v1/search/suggest?q=` | Suggestions tolérantes aux fautes |
| `GET /api/v1/circuits`, `/circuits/{id}` | Circuits éditoriaux |
| `POST /api/v1/fiches/{id}/reports` | Signalement d'information incorrecte |

## Recherche

`App\Services\Search\SearchEngine` est le seul point d'entrée de la recherche.
Implémentation actuelle : `MysqlSearchEngine` (FULLTEXT + collation `utf8mb4_0900_ai_ci` ; suggestions tolérantes
aux fautes calculées en PHP à partir d'un index en cache Redis). Pour passer à Meilisearch : ajouter une
implémentation et la sélectionner via `EKOTRIP_SEARCH_ENGINE`, sans toucher à l'API.

## Suivi

- Plan et décisions de la phase 1 : [`docs/plans/PHASE_1_SOCLE.md`](docs/plans/PHASE_1_SOCLE.md)
- Recette technique : [`docs/plans/RECETTE_PHASE_1.md`](docs/plans/RECETTE_PHASE_1.md)
