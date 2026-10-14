<?php

namespace App\Services\Import;

use App\Models\Category;
use App\Models\Circuit;
use App\Models\CircuitNote;
use App\Models\CircuitStep;
use App\Models\Fiche;
use App\Models\FichePoi;
use App\Models\ImportRun;
use App\Models\MapGroup;
use App\Models\PrecisionGeoRule;
use App\Models\ProActivityLink;
use App\Models\Setting;
use App\Services\Catalogue\CatalogueCache;
use App\Services\Catalogue\EcoBadgeResolver;
use App\Services\Catalogue\GeoClassifier;
use App\Services\Catalogue\Text;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Throwable;

/**
 * Import du classeur canonique EKOTRIP_FINAL.xlsx (Lot 1.1 §8).
 *
 * - Upsert par `id` uniquement ; aucune déduplication sur téléphone, site ou adresse.
 * - Aucune valeur inventée : une donnée invalide est écartée et signalée, jamais « réparée ».
 * - Idempotent : relancer l'import produit le même état.
 * - Rapport complet (import_runs / import_issues).
 */
class CatalogueImporter
{
    private const SHEETS = [
        '01_FICHES_APP', 'CATEGORIES', '02_PATRIMOINE_POI', '03_ACTIVITES_POI',
        '04_PRO_ACTIVITES', '05_CIRCUITS', '05A_CIRCUITS_META', '05D_NOTES_CIRCUITS',
    ];

    /** Colonnes de 01_FICHES_APP copiées telles quelles (après nettoyage). */
    private const TEXT_FIELDS = [
        'nom', 'adresse', 'code_postal', 'commune', 'categorie', 'publication', 'content_type',
        'precision_geo', 'description', 'telephone', 'site_internet', 'google_maps', 'instagram',
        'facebook', 'horaires', 'tarif_indicatif', 'services_equipements', 'engagements_eco',
        'public_cible', 'duree_recommandee', 'budget', 'ambiance', 'interieur_exterieur',
        'niveau_activite', 'accessibilite', 'tags_ia', 'mode_reservation', 'url_reservation',
        'statut_partenaire', 'niveau_ekotrip', 'statut_evaluation', 'id_activites_associees',
        'activites_associees', 'source_principale', 'source_geolocalisation',
    ];

    private ImportReport $report;

    /** @var array<string, array<string,mixed>> fiches importées, par id */
    private array $fiches = [];

    public function __construct(
        private readonly WorkbookReader $reader,
        private readonly EcoBadgeResolver $ecoBadges,
    ) {}

    public function run(bool $dryRun = false): ImportRun
    {
        $path = config('ekotrip.import.workbook');
        $this->report = new ImportReport($path, $dryRun);
        $this->fiches = [];

        try {
            $sheets = $this->reader->read($path, self::SHEETS);
            $json = $this->loadJson(config('ekotrip.import.catalogue_json'));
            $geojson = $this->loadJson(config('ekotrip.import.carte_geojson'));

            DB::beginTransaction();

            Setting::ensureDefaults();
            $categories = $this->importCategories(WorkbookReader::associate($sheets['CATEGORIES']));
            $this->seedPrecisionRules($json);
            $this->importFiches(WorkbookReader::associate($sheets['01_FICHES_APP']), $categories);
            $this->importPois(WorkbookReader::associate($sheets['02_PATRIMOINE_POI']), '02_PATRIMOINE_POI', 'point_interet', 'commune');
            $this->importPois(WorkbookReader::associate($sheets['03_ACTIVITES_POI']), '03_ACTIVITES_POI', 'point_interet_ou_modele', 'commune_zone');
            $this->importProLinks($sheets['04_PRO_ACTIVITES']);
            $circuits = $this->importCircuits(WorkbookReader::associate($sheets['05A_CIRCUITS_META']));
            $this->importCircuitSteps(WorkbookReader::associate($sheets['05_CIRCUITS']), $circuits);
            $this->importCircuitNotes(WorkbookReader::associate($sheets['05D_NOTES_CIRCUITS']), $circuits);
            $this->crossCheck($json, $geojson);

            if ($dryRun) {
                DB::rollBack();
            } else {
                DB::commit();
                CatalogueCache::flush();
            }

            return $this->report->finish('SUCCESS');
        } catch (Throwable $e) {
            if (DB::transactionLevel() > 0) {
                DB::rollBack();
            }
            $this->report->error('IMPORT_FAILED', $e->getMessage());

            return $this->report->finish('FAILED');
        }
    }

    // ------------------------------------------------------------------ catégories

    /** @return array<string, Category> par nom normalisé */
    private function importCategories(array $rows): array
    {
        $out = [];
        foreach ($rows as $row) {
            $name = Text::clean($row['categorie'] ?? null);
            if ($name === null) {
                continue;
            }
            $category = Category::query()->firstOrNew(['name' => $name]);
            $category->slug = Str::slug($name);
            $category->sort_order = (int) ($row['ordre'] ?? 0);
            $category->saveQuietly();
            $out[Text::normalize($name)] = $category;
        }
        $this->report->count('categories', count($out));
        $this->assignDefaultMapGroups($out);

        return $out;
    }

    /** @param array<string, Category> $categories */
    private function assignDefaultMapGroups(array $categories): void
    {
        foreach (config('ekotrip.map.default_groups') as $i => $def) {
            $group = MapGroup::query()->firstOrCreate(
                ['slug' => $def['slug']],
                ['label' => $def['label'], 'icon' => $def['icon'], 'sort_order' => $i + 1],
            );
            foreach ($def['categories'] as $name) {
                $category = $categories[Text::normalize($name)] ?? null;
                if ($category && $category->map_group_id === null) {
                    $category->map_group_id = $group->id;
                    $category->saveQuietly();
                }
            }
        }
        foreach ($categories as $category) {
            if ($category->map_group_id === null) {
                $this->report->warning('CATEGORY_WITHOUT_GROUP', "Catégorie « {$category->name} » sans groupe carte.", 'CATEGORIES');
            }
        }
    }

    // ------------------------------------------------------------------ precision_geo

    /**
     * Initialise precision_geo_rules depuis les couples observés dans l'export JSON
     * (fiches POINT avec coordonnées). Les règles saisies en back-office (origin ADMIN)
     * ne sont jamais écrasées.
     */
    private function seedPrecisionRules(?array $json): void
    {
        if (! $json) {
            $this->report->warning('JSON_MISSING', 'EKOTRIP_CATALOGUE.json absent : règles precision_geo non initialisées.');

            return;
        }

        $pairs = [];
        foreach ($json['records'] ?? [] as $r) {
            if (($r['content_type'] ?? null) !== 'POINT' || $r['latitude'] === null || $r['precision_geo'] === null) {
                continue;
            }
            $pairs[$r['precision_geo']][$r['geo_display_type']] = true;
        }

        $created = 0;
        foreach ($pairs as $label => $types) {
            if (count($types) > 1) {
                $this->report->warning('PRECISION_RULE_CONFLICT', "precision_geo « {$label} » associé à plusieurs classes (".implode(', ', array_keys($types)).') : règle non créée.');

                continue;
            }
            $rule = PrecisionGeoRule::query()->firstOrNew(['precision_geo' => $label]);
            if ($rule->exists && $rule->origin === 'ADMIN') {
                continue;
            }
            $rule->geo_display_type = array_key_first($types);
            $rule->origin = 'IMPORT';
            $created += $rule->isDirty() ? 1 : 0;
            $rule->save();
        }
        $this->report->count('precision_rules', count($pairs));
    }

    // ------------------------------------------------------------------ fiches

    /** @param array<string, Category> $categories */
    private function importFiches(array $rows, array $categories): void
    {
        $geo = new GeoClassifier;
        $seen = [];
        $bbox = config('ekotrip.geo.bbox');

        foreach ($rows as $row) {
            $ref = 'ligne '.$row['__row'];
            $id = Text::clean($row['id'] ?? null);
            $this->report->increment('fiches_read');

            if ($id === null) {
                $this->report->error('ROW_MISSING_ID', 'Ligne sans id : rejetée.', '01_FICHES_APP', $ref);
                $this->report->increment('fiches_rejected');

                continue;
            }
            if (isset($seen[$id])) {
                $this->report->error('DUPLICATE_ID', "id {$id} déjà présent ligne {$seen[$id]} : seconde occurrence rejetée.", '01_FICHES_APP', $id);
                $this->report->increment('fiches_rejected');

                continue;
            }
            $seen[$id] = $row['__row'];

            $data = [];
            foreach (self::TEXT_FIELDS as $field) {
                $data[$field] = Text::clean($row[$field] ?? null);
            }
            if ($data['nom'] === null) {
                $this->report->error('MISSING_NAME', "Fiche {$id} sans nom : rejetée.", '01_FICHES_APP', $id);
                $this->report->increment('fiches_rejected');

                continue;
            }

            $data['is_active'] = $this->bool($row['is_active'] ?? null) === true;
            $data['show_on_map'] = $this->bool($row['show_on_map'] ?? null) === true;
            $data['reservable_ekotrip'] = $this->bool($row['reservable_ekotrip'] ?? null);
            $data['score_ekotrip'] = is_numeric($row['score_ekotrip'] ?? null) ? (float) $row['score_ekotrip'] : null;

            // Coordonnées : conservées seulement si complètes, numériques et en Martinique.
            [$lat, $lng] = [$row['latitude'] ?? null, $row['longitude'] ?? null];
            $lat = is_numeric($lat) ? (float) $lat : null;
            $lng = is_numeric($lng) ? (float) $lng : null;
            if (($lat === null) !== ($lng === null)
                || ($lat !== null && ($lat < $bbox['min_lat'] || $lat > $bbox['max_lat'] || $lng < $bbox['min_lng'] || $lng > $bbox['max_lng']))) {
                $this->report->error('INVALID_COORDINATES', "Fiche {$id} : coordonnées incomplètes ou hors Martinique ({$row['latitude']}, {$row['longitude']}) ignorées.", '01_FICHES_APP', $id);
                [$lat, $lng] = [null, null];
            }
            // Précision de stockage decimal(10,7) ≈ 1 cm : arrondi explicite pour un import idempotent.
            $data['latitude'] = $lat === null ? null : round($lat, 7);
            $data['longitude'] = $lng === null ? null : round($lng, 7);

            // Placeholders techniques → NULL public, valeur brute conservée en interne.
            $raw = [];
            foreach (config('ekotrip.placeholder_fields') as $field) {
                if ($data[$field] !== null && in_array(Text::normalize($data[$field]), config('ekotrip.placeholders'), true)) {
                    $raw[$field] = $data[$field];
                    $data[$field] = null;
                }
            }
            $data['raw_placeholders'] = $raw ?: null;
            if ($raw && $data['is_active']) {
                $this->report->increment('placeholders_neutralized', count($raw));
                $this->report->info('PLACEHOLDER_NEUTRALIZED', "Fiche {$id} : ".implode(', ', array_map(fn ($f, $v) => "{$f} = « {$v} »", array_keys($raw), $raw)).' → non renseigné.', '01_FICHES_APP', $id);
            }

            $data['publication_status'] = $this->publicationStatus($id, $data['publication'], $data['is_active']);
            $data['is_editorial'] = in_array(Text::normalize($data['publication']), config('ekotrip.editorial_publications'), true);
            $data['budget_data_status'] = $data['budget'] === null ? 'UNKNOWN' : 'KNOWN';

            $g = $geo->classify($data['precision_geo'], $data['content_type'], $lat, $lng);
            $data['geo_display_type'] = $g['type'];
            $data['geo_display_label'] = $g['label'];
            $data['geo_navigation_allowed'] = $g['navigation_allowed'];
            if (! $g['known_rule'] && $data['is_active']) {
                $this->report->warning('UNKNOWN_PRECISION_GEO', "Fiche {$id} : precision_geo « {$data['precision_geo']} » sans règle → {$g['type']}.", '01_FICHES_APP', $id);
            }

            $category = $categories[Text::normalize($data['categorie'])] ?? null;
            if (! $category) {
                $this->report->error('UNKNOWN_CATEGORY', "Fiche {$id} : catégorie « {$data['categorie']} » absente de CATEGORIES ; aucune association créée.", '01_FICHES_APP', $id);
            }

            $fiche = Fiche::query()->find($id) ?? new Fiche(['id' => $id]);
            $isNew = ! $fiche->exists;
            $fiche->fill($data);
            $fiche->saveQuietly();
            $this->report->increment($isNew ? 'fiches_created' : ($fiche->wasChanged() ? 'fiches_updated' : 'fiches_unchanged'));

            if ($category) {
                // La catégorie source devient la principale ; les catégories secondaires saisies en back-office sont conservées.
                DB::table('category_fiche')->where('fiche_id', $id)->update(['is_primary' => false]);
                $fiche->categories()->syncWithoutDetaching([$category->id => ['is_primary' => true]]);
                $fiche->refreshSearchCategories();
            }

            // Le badge n'est jamais lu depuis niveau_ekotrip / score_ekotrip : seulement depuis une évaluation validée.
            $badge = $this->ecoBadges->resolve($fiche);
            if ($fiche->eco_badge_public !== $badge) {
                $fiche->forceFill(['eco_badge_public' => $badge])->saveQuietly();
            }

            $this->fiches[$id] = ['public' => $fiche->isPublic(), 'fiche' => $fiche];
            $this->collectFicheStats($fiche);
        }
    }

    private function publicationStatus(string $id, ?string $publication, bool $isActive): string
    {
        $key = Text::normalize($publication);
        $map = config('ekotrip.publication_map');

        if (isset($map[$key])) {
            $status = $map[$key];
        } elseif (str_starts_with($key, config('ekotrip.publication_archive_prefix'))) {
            $status = Fiche::STATUS_ARCHIVED;
        } else {
            $this->report->warning('UNKNOWN_PUBLICATION', "Fiche {$id} : publication « {$publication} » inconnue → non publique.", '01_FICHES_APP', $id);
            $status = Fiche::STATUS_UNPUBLISHED;
        }

        if (! $isActive && $status === Fiche::STATUS_PUBLISHED) {
            $this->report->warning('INACTIVE_PUBLIABLE', "Fiche {$id} : publication « {$publication} » mais is_active = FALSE → unpublished.", '01_FICHES_APP', $id);
            $status = Fiche::STATUS_UNPUBLISHED;
        }

        return $status;
    }

    private function collectFicheStats(Fiche $fiche): void
    {
        $id = $fiche->id;
        $this->report->increment('status_'.$fiche->publication_status);
        if ($fiche->is_active) {
            $this->report->increment('fiches_active');
        }
        if (! $fiche->isPublic()) {
            return;
        }
        $this->report->increment('fiches_public');
        if ($fiche->latitude === null) {
            $this->report->increment('public_without_coordinates');
        }
        if ($fiche->show_on_map && $fiche->latitude === null && ! $fiche->is_editorial) {
            $this->report->warning('TO_GEOCODE', "Fiche {$id} ({$fiche->nom}) : à géocoder — show_on_map = TRUE sans coordonnées ; aucun marqueur.", '01_FICHES_APP', $id);
        }
        if ($fiche->show_on_map && $fiche->latitude !== null && $fiche->geo_display_type === 'NON_NAVIGABLE' && ! $fiche->is_editorial) {
            $this->report->warning('TO_GEOCODE', "Fiche {$id} ({$fiche->nom}) : à géocoder — show_on_map = TRUE mais NON_NAVIGABLE (« {$fiche->precision_geo} ») ; ".(config('ekotrip.map.include_non_navigable') ? 'affichée sans itinéraire.' : 'retirée de la carte, visible en liste et recherche.'), '01_FICHES_APP', $id);
        }
        if ($fiche->is_editorial && $fiche->show_on_map) {
            $this->report->warning('EDITORIAL_ON_MAP', "Fiche {$id} : contenu éditorial marqué show_on_map = TRUE ; jamais affiché sur la carte.", '01_FICHES_APP', $id);
        }
        if ($fiche->isOnMap()) {
            $this->report->increment('fiches_on_map');
        }
    }

    // ------------------------------------------------------------------ POI / relations

    private function importPois(array $rows, string $sheet, string $labelColumn, string $communeColumn): void
    {
        FichePoi::query()->where('source_sheet', $sheet)->delete();
        $count = 0;
        foreach ($rows as $row) {
            $parent = Text::clean($row['parent_id'] ?? null);
            $label = Text::clean($row[$labelColumn] ?? null);
            if (! $parent || ! $label) {
                continue;
            }
            if (! isset($this->fiches[$parent])) {
                $this->report->warning('BROKEN_REFERENCE', "{$sheet} : fiche parente {$parent} inexistante ; ligne ignorée.", $sheet, 'ligne '.$row['__row']);

                continue;
            }
            FichePoi::query()->create([
                'fiche_id' => $parent,
                'source_sheet' => $sheet,
                'type' => Text::clean($row['type'] ?? null) ?? 'POI',
                'ordre' => (int) ($row['ordre'] ?? 0),
                'label' => $label,
                'commune' => Text::clean($row[$communeColumn] ?? null),
                'localisation' => Text::clean($row['localisation'] ?? null),
                'note_dev' => Text::clean($row['note_dev'] ?? null),
            ]);
            $count++;
        }
        $this->report->increment('pois', $count);
    }

    /** 04_PRO_ACTIVITES : lu par position (en-têtes partiellement vides dans la source). */
    private function importProLinks(array $rows): void
    {
        array_shift($rows);
        $count = 0;
        foreach ($rows as $i => $row) {
            [$pro, $activity] = [Text::clean($row[0] ?? null), Text::clean($row[2] ?? null)];
            if (! $pro || ! $activity) {
                continue;
            }
            $ref = 'ligne '.($i + 2);
            foreach ([$pro, $activity] as $fid) {
                if (! isset($this->fiches[$fid])) {
                    $this->report->warning('BROKEN_REFERENCE', "04_PRO_ACTIVITES : fiche {$fid} inexistante ; lien ignoré.", '04_PRO_ACTIVITES', $ref);

                    continue 2;
                }
            }
            $show = $this->bool($row[6] ?? null) === true;
            if ($show && (! $this->fiches[$pro]['public'] || ! $this->fiches[$activity]['public'])) {
                $this->report->warning('REFERENCE_NOT_PUBLIC', "04_PRO_ACTIVITES : lien {$pro} → {$activity} marqué « Afficher » mais une des fiches n'est pas publique ; non exposé.", '04_PRO_ACTIVITES', $ref);
            }
            ProActivityLink::query()->updateOrCreate(
                ['pro_fiche_id' => $pro, 'activity_fiche_id' => $activity],
                [
                    'statut_relation' => Text::clean($row[4] ?? null),
                    'note_interne' => Text::clean($row[5] ?? null),
                    'show_in_app' => $show,
                ],
            );
            $count++;
        }
        $this->report->increment('pro_links', $count);
    }

    // ------------------------------------------------------------------ circuits

    /** @return array<string, string> nom normalisé → circuit_id */
    private function importCircuits(array $rows): array
    {
        $map = [];
        foreach ($rows as $i => $row) {
            $id = Text::clean($row['circuit_id'] ?? null);
            $nom = Text::clean($row['nom'] ?? null);
            if (! $id || ! $nom) {
                continue;
            }
            $circuit = Circuit::query()->find($id) ?? new Circuit(['id' => $id]);
            $circuit->fill([
                'nom' => $nom,
                'resume' => Text::clean($row['resume'] ?? null),
                'duree_totale' => Text::clean($row['duree_totale'] ?? null),
                'nb_jours' => is_numeric($row['nb_jours'] ?? null) ? (int) $row['nb_jours'] : null,
                'distance_totale' => Text::clean($row['distance_totale'] ?? null),
                'difficulte' => Text::clean($row['difficulte'] ?? null),
                'mode_deplacement' => Text::clean($row['mode_deplacement'] ?? null),
                'point_depart' => Text::clean($row['point_depart'] ?? null),
                'latitude_depart' => is_numeric($row['latitude_depart'] ?? null) ? (float) $row['latitude_depart'] : null,
                'longitude_depart' => is_numeric($row['longitude_depart'] ?? null) ? (float) $row['longitude_depart'] : null,
                'trace' => Text::clean($row['trace'] ?? null),
                'photo_principale' => Text::clean($row['photo_principale'] ?? null),
                'communes' => Text::clean($row['communes'] ?? null),
                'categories' => Text::clean($row['categories'] ?? null),
                'nb_etapes' => is_numeric($row['nb_etapes'] ?? null) ? (int) $row['nb_etapes'] : null,
                'statut' => Text::clean($row['statut'] ?? null),
                'notes' => Text::clean($row['notes'] ?? null),
                'sort_order' => $i + 1,
            ])->save();
            $circuit->steps()->delete();
            $circuit->notes()->delete();
            $map[Text::normalize($nom)] = $id;
        }
        $this->report->count('circuits', count($map));

        return $map;
    }

    private function importCircuitSteps(array $rows, array $circuits): void
    {
        $positions = [];
        $declared = [];
        foreach ($rows as $row) {
            $ref = 'ligne '.$row['__row'];
            $circuitId = $circuits[Text::normalize($row['Circuit'] ?? null)] ?? null;
            if (! $circuitId) {
                $this->report->error('CIRCUIT_UNKNOWN', "05_CIRCUITS : circuit « {$row['Circuit']} » absent de 05A_CIRCUITS_META ; étape ignorée.", '05_CIRCUITS', $ref);

                continue;
            }
            if (! preg_match('/(\d+)/', (string) ($row['Jour'] ?? ''), $m)) {
                $this->report->error('STEP_DAY_INVALID', "05_CIRCUITS : jour « {$row['Jour']} » illisible ; étape ignorée.", '05_CIRCUITS', $ref);

                continue;
            }
            $day = (int) $m[1];
            $position = $positions[$circuitId][$day] = ($positions[$circuitId][$day] ?? 0) + 1;

            $ficheId = Text::clean($row['ID fiche maître'] ?? null);
            $label = Text::clean($row['Étape'] ?? null) ?? '';
            if ($ficheId && ! isset($this->fiches[$ficheId])) {
                $this->report->error('BROKEN_REFERENCE', "Circuit {$circuitId} J{$day} « {$label} » : fiche {$ficheId} inexistante.", '05_CIRCUITS', $ref);
                $ficheId = null;
            }

            $decision = Text::clean($row['Décision circuit'] ?? null);
            $retained = ! preg_match('/REMPLACER|RETIRER/', Text::normalize($decision));
            if (! $retained) {
                $this->report->warning('STEP_TO_REPLACE', "Circuit {$circuitId} J{$day} « {$label} » ({$ficheId}) : à remplacer — décision « {$decision} » ; étape importée mais jamais affichée.", '05_CIRCUITS', $ref);
            }
            if ($retained && $ficheId && ! $this->fiches[$ficheId]['public']) {
                $this->report->warning('REFERENCE_NOT_PUBLIC', "Circuit {$circuitId} J{$day} « {$label} » : fiche {$ficheId} non publique → étape non servie.", '05_CIRCUITS', $ref);
            }

            CircuitStep::query()->create([
                'circuit_id' => $circuitId,
                'day' => $day,
                'position' => $position,
                'label' => $label,
                'commune' => Text::clean($row['Commune'] ?? null),
                'fiche_id' => $ficheId,
                'categorie' => Text::clean($row['Catégorie'] ?? null),
                'sous_categorie' => Text::clean($row['Sous-catégorie'] ?? null),
                'note' => Text::clean($row['Note'] ?? null),
                'decision' => $decision,
                'consigne' => Text::clean($row['Consigne ÉKO TRIP'] ?? null),
                'statut_etape' => Text::clean($row['Statut étape'] ?? null),
                'is_retained' => $retained,
            ]);
            $this->report->increment('circuit_steps');
            if ($retained && $ficheId && $this->fiches[$ficheId]['public']) {
                $this->report->increment('circuit_steps_servable');
            }
        }
    }

    private function importCircuitNotes(array $rows, array $circuits): void
    {
        $positions = [];
        foreach ($rows as $row) {
            $ref = 'ligne '.$row['__row'];
            $circuitId = $circuits[Text::normalize($row['Circuit'] ?? null)] ?? null;
            if (! $circuitId) {
                $this->report->error('CIRCUIT_UNKNOWN', "05D_NOTES_CIRCUITS : circuit « {$row['Circuit']} » inconnu ; note ignorée.", '05D_NOTES_CIRCUITS', $ref);

                continue;
            }
            $kindRaw = Text::normalize($row['Jour'] ?? null);
            $kind = str_starts_with($kindRaw, 'HEBERGEMENT') ? CircuitNote::KIND_HEBERGEMENT
                : (str_starts_with($kindRaw, 'PRECAUTION') ? CircuitNote::KIND_PRECAUTION : null);
            if (! $kind) {
                $this->report->warning('NOTE_KIND_UNKNOWN', "05D_NOTES_CIRCUITS : type « {$row['Jour']} » inconnu ; note ignorée.", '05D_NOTES_CIRCUITS', $ref);

                continue;
            }
            // Hébergement : nom dans « Étape » ; Précaution : texte dans « Note ».
            $text = $kind === CircuitNote::KIND_PRECAUTION
                ? Text::clean($row['Note'] ?? null)
                : Text::clean($row['Étape'] ?? null);
            if (! $text) {
                $this->report->warning('NOTE_EMPTY', "05D_NOTES_CIRCUITS : note {$kind} sans texte ; ignorée.", '05D_NOTES_CIRCUITS', $ref);

                continue;
            }
            $ficheId = Text::clean($row['ID fiche maître'] ?? null);
            if ($ficheId && ! isset($this->fiches[$ficheId])) {
                $this->report->error('BROKEN_REFERENCE', "Circuit {$circuitId} note « {$text} » : fiche {$ficheId} inexistante.", '05D_NOTES_CIRCUITS', $ref);
                $ficheId = null;
            }
            CircuitNote::query()->create([
                'circuit_id' => $circuitId,
                'kind' => $kind,
                'position' => $positions[$circuitId] = ($positions[$circuitId] ?? 0) + 1,
                'text' => $text,
                'fiche_id' => $ficheId,
                'decision' => Text::clean($row['Décision circuit'] ?? null),
                'consigne' => Text::clean($row['Consigne ÉKO TRIP'] ?? null),
            ]);
            $this->report->increment('circuit_notes');
        }
    }

    // ------------------------------------------------------------------ contrôle croisé

    /**
     * Compare l'état importé aux exports livrés (JSON 383 fiches actives, GeoJSON 361 points).
     * Tout écart est signalé ; rien n'est corrigé automatiquement.
     */
    private function crossCheck(?array $json, ?array $geojson): void
    {
        if ($json) {
            $records = collect($json['records'] ?? [])->keyBy('id');
            $active = collect($this->fiches)->filter(fn ($f) => $f['fiche']->is_active)->keys();
            $this->report->count('json_records', $records->count());

            foreach ($records->keys()->diff($active) as $id) {
                $this->report->error('JSON_ID_MISMATCH', "{$id} présent dans le JSON mais pas actif dans le classeur.", 'EKOTRIP_CATALOGUE.json', $id);
            }
            foreach ($active->diff($records->keys()) as $id) {
                $this->report->error('JSON_ID_MISMATCH', "{$id} actif dans le classeur mais absent du JSON.", 'EKOTRIP_CATALOGUE.json', $id);
            }

            $mismatches = 0;
            foreach ($records as $id => $r) {
                $fiche = $this->fiches[$id]['fiche'] ?? null;
                if (! $fiche) {
                    continue;
                }
                $checks = [
                    'nom' => [$r['nom'], $fiche->nom],
                    'show_on_map' => [(bool) $r['show_on_map'], $fiche->show_on_map],
                    'latitude' => [$r['latitude'] === null ? null : round((float) $r['latitude'], 7), $fiche->latitude === null ? null : round($fiche->latitude, 7)],
                    'longitude' => [$r['longitude'] === null ? null : round((float) $r['longitude'], 7), $fiche->longitude === null ? null : round($fiche->longitude, 7)],
                    'geo_display_type' => [$r['geo_display_type'], $fiche->geo_display_type],
                    'geo_display_label' => [$r['geo_display_label'], $fiche->geo_display_label],
                    'geo_navigation_allowed' => [(bool) $r['geo_navigation_allowed'], $fiche->geo_navigation_allowed],
                    'budget_data_status' => [$r['budget_data_status'], $fiche->budget_data_status],
                ];
                foreach ($checks as $field => [$expected, $actual]) {
                    if ($expected !== $actual) {
                        $mismatches++;
                        $this->report->error('JSON_FIELD_MISMATCH', "{$id}.{$field} : JSON = ".json_encode($expected, JSON_UNESCAPED_UNICODE).', import = '.json_encode($actual, JSON_UNESCAPED_UNICODE).'.', 'EKOTRIP_CATALOGUE.json', $id);
                    }
                }
            }
            $this->report->count('json_field_mismatches', $mismatches);

            if (($json['metadata']['version'] ?? null) !== null) {
                $this->report->info('JSON_VERSION', 'Version déclarée par EKOTRIP_CATALOGUE.json : '.$json['metadata']['version'].'.', 'EKOTRIP_CATALOGUE.json');
            }
        }

        if ($geojson) {
            $features = collect($geojson['features'] ?? [])->keyBy(fn ($f) => $f['properties']['id'] ?? null);
            $expected = collect($this->fiches)
                ->filter(fn ($f) => $f['fiche']->is_active && $f['fiche']->show_on_map && $f['fiche']->latitude !== null)
                ->keys();
            $this->report->count('geojson_features', $features->count());
            foreach ($features->keys()->diff($expected) as $id) {
                $this->report->error('GEOJSON_MISMATCH', "{$id} présent dans le GeoJSON mais non cartographiable dans le classeur.", 'EKOTRIP_CARTE.geojson', $id);
            }
            foreach ($expected->diff($features->keys()) as $id) {
                $this->report->error('GEOJSON_MISMATCH', "{$id} cartographiable dans le classeur mais absent du GeoJSON.", 'EKOTRIP_CARTE.geojson', $id);
            }
            foreach ($features as $id => $f) {
                $fiche = $this->fiches[$id]['fiche'] ?? null;
                [$lng, $lat] = $f['geometry']['coordinates'] ?? [null, null];
                if ($fiche && $fiche->latitude !== null && (round((float) $lat, 7) !== round($fiche->latitude, 7) || round((float) $lng, 7) !== round($fiche->longitude, 7))) {
                    $this->report->error('GEOJSON_MISMATCH', "{$id} : coordonnées GeoJSON différentes du classeur.", 'EKOTRIP_CARTE.geojson', $id);
                }
            }
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private function bool(mixed $value): ?bool
    {
        if (is_bool($value)) {
            return $value;
        }
        $v = Text::normalize(is_scalar($value) ? (string) $value : null);

        return match ($v) {
            'TRUE', 'VRAI', '1', 'OUI' => true,
            'FALSE', 'FAUX', '0', 'NON' => false,
            default => null,
        };
    }

    private function loadJson(?string $path): ?array
    {
        if (! $path || ! is_file($path)) {
            return null;
        }

        return json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
    }
}
