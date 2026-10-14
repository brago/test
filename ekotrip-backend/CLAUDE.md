# ÉKO TRIP Martinique — Instructions pour Claude Code

## Le projet
Application mobile de tourisme responsable en Martinique. Ce dépôt contient le **backend Laravel** :
API REST pour l'app mobile, back-office d'administration/modération, espace professionnel web.

## Documentation de référence (dossier `docs/`)
Ordre de priorité en cas de contradiction :
1. `docs/data/EKOTRIP_FINAL.xlsx` — classeur canonique V1.2 (schémas, enums, relations, règles). Onglets clés :
   `01_FICHES_APP`, `CATEGORIES`, `05_CIRCUITS`, `30_CIRCUITS_FINAL`, `06_MEDIAS`, `08_DICTIONNAIRE`,
   `62_EXTENSIONS_SCHEMA`, `63_CARTO_ROUTAGE_V1`, `77_GARDEFOUS_DONNEES_V11`, `78_MEDIAS_SOURCES_V11`,
   `79_EVENTS_SCHEMA_V1` à `84_RECETTE_EVENTS_V1`, `50`–`53` (scoring Mon Ékotrip), `65`–`76` (pro, réservation, paiement).
2. `docs/specs/EKOTRIP_SPECIFICATIONS_FONCTIONNELLES_TECHNIQUES_V12_CORRIGEES.pdf`
3. `docs/specs/EKOTRIP_08_DETAIL_MAQUETTES_UI_UX_V12.pdf`
4. `docs/specs/EKOTRIP_ADDENDUM_LOTS_1_A_5_V12.pdf`
5. `docs/lots/` — sous-lots 1.1 à 5.7 (valables quand non modifiés par l'addendum)
6. `docs/data/` — `EKOTRIP_CATALOGUE.json/.csv`, `EKOTRIP_CARTE.geojson`, dictionnaires import/events/médias.

Avant de coder une fonctionnalité, lis l'onglet du classeur et le sous-lot concernés. Si une règle est ambiguë, **pose la question** plutôt que de supposer.

## Stack
- PHP 8.3, Laravel 11+, MySQL 8 (ou PostgreSQL), Redis (cache, sessions, queues), Laravel Horizon, scheduler.
- Auth API mobile : Laravel Sanctum. Back-office : à proposer (Filament recommandé).
- Médias : stockage objet S3-compatible via `Storage`, redimensionnement en job de queue.
- Tests : Pest ou PHPUnit. Chaque règle non négociable ci-dessous doit avoir un test.

## Règles non négociables (ne jamais déroger)
- **Ne jamais inventer** GPS, prix, horaires, disponibilité, temps de trajet, note, badge, récurrence ou date de vérification. `NULL` = non renseigné.
- **IDs existants stables** : l'import conserve les `id` du catalogue. Une entité réelle = une seule fiche maître ; multi-catégories via table pivot.
- Fiches archivées / non publiables : jamais dans l'API publique, la recherche, la carte, Mon Ékotrip ni les suggestions.
- **Carte** : seulement fiches actives, `show_on_map = TRUE` et coordonnées valides. `precision_geo` / `geo_display_type` ∈ `EXACT`, `ACCESS_OR_DEPARTURE`, `INDICATIVE`, `NON_NAVIGABLE` ; `NON_NAVIGABLE` interdit le CTA itinéraire.
- **Badge éco** : `eco_badge_public` uniquement après évaluation validée et preuves. Les valeurs legacy A/A+ ne sont jamais un badge public. Abonnement/sponsoring n'influencent jamais le badge. « Vérifié » ≠ écoresponsable.
- **Médias** : table générique `target_type` ∈ `FICHE | CIRCUIT | EVENT`. Sources `GOOGLE_PLACES | OPEN_LICENSE | PARTNER_UPLOAD | EKOTRIP_OWNED`. Seul le statut `ACTIVE` est servi ; `TO_VERIFY` / `BLOCKED` jamais. Pas de stockage local des photos Google Places.
- **Événements** : structure séparée des fiches. Public = `PUBLISHED` + `APPROVED`. Statuts `DRAFT, PENDING_REVIEW, PUBLISHED, CANCELLED, POSTPONED, ENDED, ARCHIVED`. Passage auto en `ENDED` via scheduler. Prix inconnu ≠ gratuit (`Gratuit` seulement si `is_free = TRUE`). Pas d'héritage du badge éco du lieu. Pas de faux marqueur sans GPS.
- **Mon Ékotrip** : scoring déterministe, seuil 60. Un événement = étape `ANCHOR_EVENT` à horaire fixe, jamais déplacée ; conflits signalés ; annulation/report invalide l'étape.
- **Réservation** : canaux `NONE | PARTNER_SITE | DIRECT_CONTACT | IN_APP` (IN_APP seulement si infrastructure active). `booking_status` distinct de `payment_status`. Clic billetterie externe ≠ booking. Webhooks vérifiés + clés d'idempotence. Aucune donnée carte bancaire stockée.
- **Montants commerciaux configurables** (19 €/mois, 8 % de commission) : en base/config, jamais codés en dur.
- Permissions toujours contrôlées côté serveur (policies Laravel).

## Navigation produit (pour la forme de l'API)
Explorer | Découvrir (inclut Agenda / Événements) | Adresses locales | Mon Ékotrip | Profil. Pas de 6e onglet.

## Ordre de développement
1. Socle : schéma, import catalogue avec rapport, API catalogue/carte/recherche, back-office de contrôle.
2. Mon Ékotrip. 3. Agenda / Événements. 4. Comptes. 5. Espace pro. 6. Réservation. 7. Passeport / avis. 8. Production.
Ne pas commencer une phase avant que la recette de la précédente passe.

## Méthode de travail
- Avant chaque phase : proposer un plan (tables, endpoints, tests) et attendre validation.
- Petits commits avec messages clairs. Lancer les tests avant de déclarer une tâche terminée.
- Signaler toute incohérence trouvée dans les données ou les specs au lieu de la corriger silencieusement.
