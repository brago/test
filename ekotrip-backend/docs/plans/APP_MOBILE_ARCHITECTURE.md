# Application mobile ÉKO TRIP (Flutter) : proposition d'architecture

> Statut : **PROPOSITION, en attente de validation**. Aucun code n'est écrit.
> Développement prévu en local sur PC Windows avec Claude Code.
> Premier objectif : faire passer sur téléphone la recette de la phase 1 (Explorer, fiches, géolocalisation, mode dégradé).
> Sources : `CLAUDE.md` backend, Lots 1.2 à 1.6, Addendum V1.2, consigne carte Explorer, `63_CARTO_ROUTAGE_V1`, API v1 (`ekotrip-backend/README.md`).

---

## 1. Dépôt : même dépôt ou dépôt dédié ?

**Recommandation : un dépôt dédié au projet, `ekotrip`, en monorepo** :

```
ekotrip/
├── ekotrip-backend/     (Laravel, déplacé tel quel avec son historique)
├── ekotrip-app/         (Flutter)
└── docs/                (documentation de référence commune, sortie de ekotrip-backend/docs)
```

| Option | Pour | Contre |
|---|---|---|
| **Dépôt dédié `ekotrip`, monorepo** (recommandé) | Une seule source de vérité pour la doc et le contrat d'API ; une pull request peut modifier l'API et l'app ensemble ; nom clair ; plus de code étranger à la racine | Une migration unique à faire (simple, historique conservé) |
| Rester dans `brago/test` | Rien à faire | Le dépôt contient à sa racine une bibliothèque Java sans rapport (`io.mifos…`) ; nom non parlant ; dépôt partagé avec cet ancien code |
| Deux dépôts séparés (backend / app) | Droits et CI indépendants | Doc et contrat d'API à synchroniser entre deux dépôts ; plus lourd pour une petite équipe |

Pour créer le dépôt dédié, j'ai besoin de votre accord. Vous le créez sur GitHub, ou vous me demandez de le faire. J'y pousserai ensuite `ekotrip-backend/` avec son historique. En attendant, `ekotrip-app/` peut très bien démarrer dans `brago/test`, à côté du backend.

---

## 2. Choix techniques

| Sujet | Choix | Pourquoi |
|---|---|---|
| Flutter / Dart | Flutter stable, Dart 3 | Null safety stricte : un `null` du backend reste un `null` jusqu'à l'écran |
| Gestion d'état | **Riverpod** (`flutter_riverpod` + `riverpod_annotation` / `riverpod_generator`) | Testable sans widget, dépendances explicites, `AsyncValue` gère les états chargement / erreur / données |
| Navigation | **go_router** avec `StatefulShellRoute.indexedStack` | 5 onglets qui gardent leur état : en revenant d'une fiche, Explorer retrouve son zoom, sa zone et ses filtres (Lot 1.4 §12) |
| Client HTTP | **dio** | Intercepteurs (URL de base, délais, en-têtes), annulation des requêtes quand la carte bouge, conversion des erreurs en un seul type |
| Modèles | **freezed** + **json_serializable** | Modèles immuables, champs nullables identiques au contrat d'API, `copyWith` pour les filtres |
| Carte | **mapbox_maps_flutter** (plugin officiel Mapbox) | Prévu dans `63_CARTO_ROUTAGE_V1` ; regroupement (clustering) natif sur une source GeoJSON |
| Géolocalisation | **geolocator** | Permission et position ; la position n'est jamais stockée (Lot 1.4 §14) |
| Liens externes | **url_launcher** | Itinéraire vers l'application de cartes du téléphone, appel, site web |
| Images | **cached_network_image** | Cache réseau et repli sur le visuel de remplacement ÉKO TRIP |
| Langue | `flutter_localizations` + fichiers ARB, français par défaut | Aucun texte en dur dans les widgets |
| Qualité | `flutter_lints` (ou `very_good_analysis`), tests unitaires, tests de widgets et `integration_test` | Les règles non négociables deviennent des tests |

---

## 3. Structure des dossiers

Une organisation par fonctionnalité. Chaque fonctionnalité a quatre couches : `data` (API), `domain` (modèles et règles), `application` (providers Riverpod) et `presentation` (écrans).

```
ekotrip-app/
├── CLAUDE.md                      règles propres à l'app (§8)
├── config/
│   ├── env.example.json           modèle versionné (valeurs factices)
│   ├── env.emulator.json          NON versionné : API_BASE_URL=10.0.2.2, clé publique Mapbox
│   └── env.phone.json             NON versionné : API_BASE_URL=IP du PC sur le Wi-Fi
├── assets/
│   ├── map_icons/                 une icône par groupe carte (clés envoyées par l'API : bed, utensils…)
│   └── placeholders/              visuel de remplacement ÉKO TRIP par catégorie
├── lib/
│   ├── main.dart                  lecture de la configuration, ProviderScope, lancement
│   ├── app.dart                   MaterialApp.router, thème, langue
│   ├── core/
│   │   ├── config/env.dart        API_BASE_URL, MAPBOX_PUBLIC_TOKEN (dart-define), vérifiés au démarrage
│   │   ├── network/               client dio, intercepteurs, conversion des erreurs → AppFailure
│   │   ├── errors/app_failure.dart
│   │   ├── routing/router.dart    go_router : shell à 5 onglets + routes /fiche/:id
│   │   ├── theme/                 couleurs de la charte (sauge, lagon, sable…), typographie, espacements
│   │   └── widgets/               composants communs : OptionalText, OptionalSection, EcoBadge,
│   │                              GeoPrecisionLabel, PlaceholderImage, ErrorView, LoadingView
│   ├── features/
│   │   ├── catalogue/             référentiels partagés : config, catégories, groupes carte, communes
│   │   ├── explorer/
│   │   │   ├── data/              map_repository (GET /map/fiches), fiche_list_repository (GET /fiches)
│   │   │   ├── domain/            ExplorerFilters, MapFeature, GeoDisplayType, règles d'affichage
│   │   │   ├── application/       filtersProvider, mapFeaturesProvider, ficheListProvider,
│   │   │   │                      selectedFicheProvider, locationController, mapAvailabilityProvider
│   │   │   └── presentation/      explorer_screen (carte ⇄ liste), map_view, fiche_list_view,
│   │   │                          filter_sheet, mini_fiche_card, map_unavailable_view,
│   │   │                          location_rationale_sheet, commune_picker
│   │   ├── fiche/                 fiche détaillée (GET /fiches/{id}) et ses variantes par type
│   │   ├── decouvrir/             écran vide (phase suivante)
│   │   ├── adresses_locales/      écran vide
│   │   ├── mon_ekotrip/           écran vide
│   │   └── profil/                écran vide
│   └── l10n/                      app_fr.arb
├── test/                          tests unitaires et de widgets
└── integration_test/              parcours de recette sur appareil
```

---

## 4. Flux de données d'Explorer

```
                      filtersProvider (catégories, commune, « autour de moi », recherche)
                       │                                   │
           mapFeaturesProvider                       ficheListProvider
         GET /api/v1/map/fiches                     GET /api/v1/fiches (paginé)
                       │                                   │
           Carte Mapbox : source GeoJSON             Vue liste (y compris les fiches
           avec regroupement au zoom                 sans coordonnées, jamais placées)
                       │                                   │
                 toucher un repère ──► mini-fiche (propriétés du GeoJSON) ──► /fiche/:id
```

- **Un seul état de filtres** pour la carte et la liste : les deux vues envoient les mêmes paramètres à l'API (`categories[]`, `communes[]`, `near`, `radius`, `q`). Le backend garantit déjà que les mêmes filtres donnent les mêmes fiches.
- **Repères** : une seule source GeoJSON avec regroupement natif Mapbox. Une couche « groupes » affiche le nombre de fiches ; une couche « repères » affiche l'icône du groupe carte (`properties.categorie.groupe.icon`) avec sa couleur. L'icône porte toujours l'information ; la couleur ne fait que la renforcer. Toucher un groupe zoome dessus.
- **Mini-fiche** : construite à partir des propriétés du repère, sans appel réseau. Elle affiche la photo si elle existe, le nom, la catégorie, la commune et le libellé de précision géographique. Elle propose « Voir la fiche » et « Itinéraire », ce dernier seulement si `navigation_allowed`.
- **Défense en profondeur** : même si l'API filtre déjà, l'app ignore tout repère sans coordonnées valides ou de type `NON_NAVIGABLE`. Elle ne place jamais de point au centre d'une commune.
- **Requêtes de carte** : anti-rebond sur les déplacements, annulation de la requête précédente (`CancelToken`), bouton « Rechercher dans cette zone » plutôt qu'un rechargement à chaque geste.

---

## 5. Gestion des erreurs et modes dégradés

Le client HTTP convertit toute erreur en un seul type, `AppFailure` : `NoConnection`, `Timeout`, `NotFound`, `Validation`, `RateLimited`, `Server`, `Unknown`. Les écrans n'affichent jamais une exception brute.

| Situation | Comportement |
|---|---|
| Fond de carte qui ne charge pas (erreur ou délai dépassé) | Écran « Carte momentanément indisponible » avec un bouton « Voir la liste des lieux » et « Réessayer ». Jamais de points sur un fond vide. |
| Pas de réseau | Message clair et « Réessayer » ; on garde ce qui est déjà affiché, sans promettre de mode hors ligne |
| Fiche devenue non publique (404) | « Cette fiche n'est plus disponible », retour à la liste |
| Trop de requêtes (429, signalements) | Message neutre et nouvel essai possible plus tard |
| Aucun résultat | Message et proposition de retirer des filtres ou de tout réinitialiser |
| Image absente ou en erreur | Visuel de remplacement ÉKO TRIP de la catégorie |

### Géolocalisation (Lots 1.4 §9 et §14)

1. Rien n'est demandé au démarrage. L'utilisateur touche d'abord « Autour de moi ».
2. Une feuille d'explication ÉKO TRIP s'affiche **avant** la demande système : à quoi sert la position, le fait qu'elle n'est pas conservée, et l'app reste utilisable sans.
3. Si l'utilisateur accepte, la carte se recentre sur sa position (point bleu distinct des repères) et la liste passe en `near=lat,lng` avec le rayon envoyé par `/config`.
4. Si l'utilisateur refuse ou a refusé définitivement, l'app propose le **choix d'une commune** (liste issue de `/communes`) et un lien vers les réglages du téléphone. La carte se cadre sur l'emprise des fiches de cette commune. Il n'y a aucun « centre de commune » inventé.

### Sans compte

Aucun écran de connexion dans cette phase. Le Profil est un écran vide. Aucune donnée personnelle n'est envoyée ou stockée.

---

## 6. Configuration et clés

| Élément | Où | Versionné ? |
|---|---|---|
| `API_BASE_URL` | `config/env.*.json`, transmis par `--dart-define-from-file` | Non (seul `env.example.json` l'est) |
| `MAPBOX_PUBLIC_TOKEN` (pk…) | `config/env.*.json`, transmis par `--dart-define-from-file`, lu au démarrage | Non |
| Clé secrète Mapbox (sk…, droit `DOWNLOADS:READ`) | Variable d'environnement Windows `MAPBOX_DOWNLOADS_TOKEN`, lue par Gradle à la compilation | **Jamais** |

- `.gitignore` de l'app : `config/*.json` sauf `env.example.json`, `android/local.properties`, `*.keystore`, `key.properties`.
- La clé publique finit forcément dans l'APK : c'est normal pour Mapbox. On lui donne seulement les droits publics nécessaires et on la surveille dans la console Mapbox (alertes de quota prévues dans `63_CARTO_ROUTAGE_V1`).
- **À vérifier à l'installation** : selon la version de `mapbox_maps_flutter`, la clé secrète peut ne plus être exigée pour télécharger le SDK Android. Si la documentation du plugin ne la demande plus, on n'en crée pas.
- **HTTP en clair** (API locale en `http://`) : autorisé **uniquement en debug**, via `android/app/src/debug/AndroidManifest.xml`. La version publiée reste en HTTPS.

Exemples d'adresse de l'API (sans `/api/v1`, ajouté par le client) :

- émulateur Android : `http://10.0.2.2:8000` (ou `http://10.0.2.2` avec Sail sur le port 80) ;
- téléphone réel : `http://192.168.x.y:8000`, l'adresse IP Wi-Fi du PC.

---

## 7. Prérequis côté backend pour la recette téléphone

1. **Couleurs des groupes carte** : elles sont vides en base. Il faut les renseigner dans le back-office (Groupes carte) d'après la charte : vert sauge / eucalyptus, bleu lagon, beige sable, corail… Je peux vous proposer une palette accessible, à valider.
2. **API accessible depuis le Wi-Fi** : `php artisan serve --host=0.0.0.0 --port=8000`, ou Sail (port 80), plus une règle du pare-feu Windows (§9).
3. **Médias** : aucun pour l'instant, donc l'app affichera partout les visuels de remplacement. C'est le comportement attendu.
4. **Communes** : correspondance appliquée. Le filtre `communes[]` attend des codes INSEE, `/communes` renvoie les 34 communes officielles et chaque fiche expose `rattachement`. L'app affiche toujours le libellé `commune` d'origine.

---

## 8. Brouillon du `CLAUDE.md` de l'app (créé au démarrage du code)

```markdown
# ÉKO TRIP Martinique — application mobile (Flutter)

## Le projet
Application mobile de tourisme responsable en Martinique. Elle consomme l'API v1 du backend
(`../ekotrip-backend`, voir son README). Le backend est la source de vérité : l'app affiche,
elle ne déduit ni ne complète jamais une donnée.

## Règles non négociables (affichage)
- **Ne jamais inventer de données** : GPS, prix, horaires, disponibilité, temps de trajet, distance
  routière, note, avis, badge, statut, date. Aucune valeur par défaut pour « faire joli ».
- **`null` = rien d'affiché** : le champ, son libellé et son icône disparaissent. Jamais « 0 € »,
  « Gratuit », « Non accessible », « Fermé » ou « — » à la place d'une valeur inconnue.
  Utiliser les widgets `OptionalText` / `OptionalSection` ; ne jamais écrire `?? ''` sur une donnée métier.
- **Aucun faux marqueur** : un repère uniquement pour une fiche reçue avec des coordonnées valides
  et `geo_display_type != NON_NAVIGABLE`. Jamais de point au centre d'une commune, d'une zone ou de la Martinique.
- **Précision géographique** toujours affichée avec son libellé (`geo_display_label`) :
  un point INDICATIVE ou ACCESS_OR_DEPARTURE n'est jamais présenté comme entrée, parking ou rendez-vous exact.
- **Pas de bouton Itinéraire** si `navigation_allowed = false` (ou si la fiche est `NON_NAVIGABLE`, ou sans coordonnées).
- **Pas de badge éco sans `eco_badge_public`** : jamais à partir d'un score, d'un niveau A/A+, d'un engagement
  ou d'une couleur de marqueur.
- **Statuts jamais portés par la seule couleur** : toujours une icône et/ou un texte (repères, badges,
  états d'événement plus tard). Contrastes conformes, libellés d'accessibilité (Semantics).
- **Pas de faux bouton** : Réserver, Appeler, Site n'apparaissent que si l'API fournit l'action
  (`actions.*`) et la donnée correspondante.
- **Médias** : uniquement ceux renvoyés par l'API ; afficher l'attribution si fournie ; sinon visuel de remplacement ÉKO TRIP.
- **Distance** : `distance_km` est une distance à vol d'oiseau pour le tri « Autour de moi » ;
  ne jamais la présenter comme un temps ou une distance de trajet.
- **Vie privée** : la position n'est jamais stockée ni journalisée ; explication avant la demande système ;
  l'app reste utilisable sans localisation et sans compte.

## Navigation
5 onglets, pas un de plus : Explorer | Découvrir (contiendra l'Agenda) | Adresses locales | Mon Ékotrip | Profil.

## Secrets
Clé publique Mapbox et adresse de l'API dans `config/env.*.json` (non versionnés, `--dart-define-from-file`).
Clé secrète Mapbox uniquement en variable d'environnement de compilation (`MAPBOX_DOWNLOADS_TOKEN`). Jamais dans Git.

## Méthode
- Lire la règle backend / le sous-lot concerné avant de coder un écran ; en cas d'ambiguïté, poser la question.
- Chaque règle non négociable a un test (unitaire ou widget).
- `flutter analyze` et `flutter test` verts avant tout commit.
```

---

## 9. Lancer l'app sur votre téléphone Android depuis Windows (pas à pas)

### A. Installer les outils (une seule fois)

1. **Git pour Windows**, puis dans un terminal : `git config --global core.longpaths true`. Flutter crée des chemins longs.
2. **Flutter SDK** (canal stable), décompressé dans un dossier **sans espace ni accent**, par exemple `C:\dev\flutter`. Ajoutez `C:\dev\flutter\bin` au `Path` (Paramètres Windows → « Modifier les variables d'environnement système »).
3. **Android Studio**. Dans *SDK Manager*, installez : *Android SDK Platform* (version récente), *Android SDK Command-line Tools*, *Android SDK Platform-Tools* et, si votre téléphone n'est pas reconnu, *Google USB Driver* (téléphones Pixel). Pour les autres marques, prenez le pilote USB du fabricant.
4. Dans un nouveau terminal : `flutter doctor`, puis `flutter doctor --android-licenses` (acceptez tout). Recommencez jusqu'à ce que `flutter doctor` ne signale plus de problème Android.
5. **Clé secrète Mapbox**, seulement si le plugin l'exige (voir §6) : `setx MAPBOX_DOWNLOADS_TOKEN "sk.xxxx"`, puis fermez et rouvrez le terminal.

### B. Préparer le téléphone

1. *Paramètres → À propos du téléphone* : touchez 7 fois *Numéro de build* pour activer les options pour les développeurs.
2. *Options pour les développeurs* : activez **Débogage USB**.
3. Branchez le téléphone en USB et acceptez « Autoriser le débogage USB » sur l'écran du téléphone.
4. Sur le PC : `adb devices` doit afficher l'appareil avec l'état `device`, puis `flutter devices` doit le lister.
   *Sans câble (Android 11 et plus)* : *Débogage sans fil* → *Associer avec un code*, puis sur le PC `adb pair IP:PORT` et `adb connect IP:PORT`.

### C. Rendre l'API joignable depuis le téléphone

1. Le PC et le téléphone doivent être sur le **même Wi-Fi**, et le profil réseau Windows doit être **Privé**.
2. Adresse IP du PC : `ipconfig`, ligne *Adresse IPv4* de la carte Wi-Fi (par exemple `192.168.1.20`).
3. Démarrer l'API :
   - sans Docker : `php artisan serve --host=0.0.0.0 --port=8000` dans `ekotrip-backend` ;
   - ou avec Sail (Docker Desktop + WSL2) : `./vendor/bin/sail up -d`, et l'API répond sur le port 80.
4. Ouvrir le pare-feu, dans un PowerShell administrateur :
   `New-NetFirewallRule -DisplayName "EKOTRIP API" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow -Profile Private`
5. Vérification : dans le navigateur **du téléphone**, ouvrez `http://192.168.1.20:8000/api/v1/config`. Le JSON doit s'afficher.

### D. Lancer l'app

1. `cd ekotrip-app`, puis copiez `config/env.example.json` vers `config/env.phone.json`. Renseignez `API_BASE_URL` (`http://192.168.1.20:8000`) et `MAPBOX_PUBLIC_TOKEN`.
2. `flutter pub get`, puis `dart run build_runner build --delete-conflicting-outputs` pour les modèles générés.
3. `flutter run --dart-define-from-file=config/env.phone.json`. Le premier lancement compile Gradle et prend plusieurs minutes.
4. Pendant le lancement : touche `r` pour recharger à chaud, `R` pour redémarrer, `q` pour quitter.
   Sur l'émulateur, utilisez `config/env.emulator.json` (`http://10.0.2.2:8000`).

---

## 10. Découpage proposé (après validation)

1. Squelette : projet Flutter, configuration, thème, go_router avec les 5 onglets (4 écrans vides), `CLAUDE.md`, lints, CI locale (`flutter analyze`, `flutter test`).
2. Couche réseau et modèles : client dio, `AppFailure`, modèles freezed alignés sur l'API v1, tests de conversion avec des réponses réelles de l'API.
3. Explorer, liste : filtres par catégorie, recherche, états vides et erreurs.
4. Explorer, carte : Mapbox, repères par groupe, regroupement, mini-fiche, carte indisponible.
5. Fiche détaillée : actions conditionnelles, précision géographique, badge, visuels de remplacement.
6. Géolocalisation : explication avant la demande système, « Autour de moi », choix d'une commune en cas de refus.
7. Recette sur téléphone : déroulé des critères marqués « App » dans `RECETTE_PHASE_1.md`, rapport.

## 11. Décisions attendues

1. **Dépôt** : dépôt dédié `ekotrip` en monorepo (recommandé), ou démarrage dans `brago/test` ?
2. **Riverpod avec génération de code** (`riverpod_generator` + `freezed`) : d'accord ? L'alternative sans génération est plus simple à lancer mais plus verbeuse.
3. **Couleurs des groupes carte** : vous les fournissez, ou je propose une palette d'après la charte, à valider ?
4. **Version minimale d'Android** : la plus basse acceptée par le plugin Mapbox, ou une version plus récente que vous préférez ?
