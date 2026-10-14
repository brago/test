<?php

/*
|--------------------------------------------------------------------------
| ÉKO TRIP — paramètres métier du socle (phase 1)
|--------------------------------------------------------------------------
| Les arbitrages de recette (décisions du 15/10/2026, docs/plans/PHASE_1_SOCLE.md §11)
| sont isolés ici pour pouvoir être modifiés sans toucher au code.
*/

return [

    'import' => [
        'workbook' => env('EKOTRIP_WORKBOOK', base_path('docs/data/EKOTRIP_FINAL.xlsx')),
        'catalogue_json' => env('EKOTRIP_CATALOGUE_JSON', base_path('docs/data/EKOTRIP_CATALOGUE.json')),
        'carte_geojson' => env('EKOTRIP_CARTE_GEOJSON', base_path('docs/data/EKOTRIP_CARTE.geojson')),
    ],

    /*
     | Valeur brute de la colonne `publication` → publication_status.
     | Validé le 14/10/2026 (décision 1). Clés comparées après normalisation
     | (majuscules, sans accents). Une valeur inconnue → non publique (unpublished)
     | + signalement UNKNOWN_PUBLICATION. Une fiche is_active = FALSE n'est jamais `published`.
     */
    'publication_map' => [
        'PUBLIABLE' => 'published',
        'CONTENU EDITORIAL' => 'published',
        'A CONTROLER' => 'pending_review',
        'A RESTRUCTURER' => 'unpublished',
        'A RECLASSER' => 'pending_review',
        'NE PAS PUBLIER' => 'archived',
        'ARCHIVE' => 'archived',
    ],
    // Toute valeur commençant par ce préfixe (ex. « ARCHIVER / établissement Trois-Îlets fermé-transféré ») → archived.
    'publication_archive_prefix' => 'ARCHIVER',

    // Contenu éditorial : public (Découvrir, liste, recherche) mais jamais sur la carte (ACT-007, ACT-063).
    'editorial_publications' => ['CONTENU EDITORIAL'],

    /*
     | Placeholders techniques jamais exposés au public (Recette globale V1.2 :
     | « Aucune valeur À_CONFIRMIR exposée »). Validé (décision 3) : l'API renvoie NULL,
     | la valeur brute est conservée en interne dans raw_placeholders.
     */
    'placeholders' => ['A CONFIRMER', 'A_CONFIRMER', 'A_CONFIRMIR', 'A DETERMINER'],
    'placeholder_fields' => [
        'adresse', 'code_postal', 'commune', 'telephone', 'site_internet', 'horaires',
        'tarif_indicatif', 'services_equipements', 'engagements_eco', 'public_cible',
        'duree_recommandee', 'budget', 'ambiance', 'interieur_exterieur', 'niveau_activite',
        'accessibilite', 'mode_reservation',
    ],

    'geo' => [
        // Libellés utilisateur (EKOTRIP_DICTIONNAIRE_IMPORT_V12 : geo_display_label).
        'labels' => [
            'EXACT' => 'Localisation précise',
            'ACCESS_OR_DEPARTURE' => "Point d'accès / départ",
            'INDICATIVE' => 'Localisation indicative',
            'NON_NAVIGABLE' => 'Localisation non confirmée',
        ],
        // content_type pour lesquels aucun point navigable n'existe (00_LIRE_MOI_DEV règle 2).
        'non_point_content_types' => ['SANS_POINT', 'MOBILE', 'EDITORIAL', 'ZONE_MULTI_POI', 'HORS_CATALOGUE'],
        // Libellé precision_geo inconnu → classe la plus prudente.
        'unknown_precision_fallback' => 'NON_NAVIGABLE',
        // Emprise de contrôle des coordonnées (Martinique).
        'bbox' => ['min_lat' => 14.3, 'max_lat' => 14.95, 'min_lng' => -61.3, 'max_lng' => -60.75],
    ],

    'map' => [
        /*
         | Validé (décision 2) : une fiche NON_NAVIGABLE n'a pas de point fiable ; elle est
         | retirée de la carte même si show_on_map = TRUE (GAS-003/009/013/016), reste en liste
         | et en recherche, et apparaît « à géocoder » dans le back-office.
         */
        'include_non_navigable' => env('EKOTRIP_MAP_INCLUDE_NON_NAVIGABLE', false),

        /*
         | Groupes carte par défaut (Lot 1.4 §4). Créés à l'import s'ils n'existent pas ;
         | une catégorie n'est rattachée automatiquement que si elle n'a pas encore de groupe
         | (les choix faits en back-office ne sont jamais écrasés).
         | « Professionnels » → groupe des Activités (« Bouger »), modifiable en back-office (décision 5).
         */
        'default_groups' => [
            ['slug' => 'dormir', 'label' => 'Dormir', 'icon' => 'bed', 'categories' => ['Hébergements']],
            ['slug' => 'manger-local', 'label' => 'Manger local', 'icon' => 'utensils', 'categories' => ['Gastronomie']],
            ['slug' => 'nature', 'label' => 'Nature', 'icon' => 'tree', 'categories' => ['Nature']],
            ['slug' => 'decouvrir', 'label' => 'Découvrir', 'icon' => 'landmark', 'categories' => ['Patrimoine & découverte']],
            ['slug' => 'bouger', 'label' => 'Bouger', 'icon' => 'person-hiking', 'categories' => ['Activités', 'Professionnels']],
            ['slug' => 'bien-etre', 'label' => 'Bien-être', 'icon' => 'spa', 'categories' => ['Spa & bien-être']],
            ['slug' => 'artisanat', 'label' => 'Artisanat', 'icon' => 'hand', 'categories' => ['Artisanat & savoir-faire']],
        ],
    ],

    'search' => [
        'engine' => env('EKOTRIP_SEARCH_ENGINE', 'mysql'),
        'suggest_limit' => 8,
        'suggest_min_chars' => 2,
        // Similarité minimale (0–1) pour la tolérance aux fautes des suggestions.
        'fuzzy_min_similarity' => 0.72,
        'cache_key' => 'ekotrip:search:suggest-index',
    ],

    'api' => [
        'per_page' => 20,
        'max_per_page' => 100,
    ],

    // Référentiel 07_REF_SCORE : score validé → niveau public.
    'eco_levels' => [
        ['min' => 80, 'label' => 'Éko Exemplaire'],
        ['min' => 60, 'label' => 'Éko Responsable'],
        ['min' => 40, 'label' => 'Éko Engagé'],
    ],

    // Valeurs par défaut des paramètres administrables (table settings).
    'settings_defaults' => [
        // Validé (décision 8) ; modifiable en back-office.
        'nearby_radius_km' => 10,
    ],

    'reports' => [
        'motifs' => ['FERMETURE', 'TELEPHONE', 'ADRESSE', 'CONTENU', 'AUTRE'],
        'per_minute' => 5,
    ],
];
