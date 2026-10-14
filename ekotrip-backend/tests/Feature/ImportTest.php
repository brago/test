<?php

use App\Models\CircuitStep;
use App\Models\Fiche;
use App\Models\ImportRun;
use App\Services\Import\CatalogueImporter;
use Illuminate\Support\Facades\DB;

function catalogueJson(): array
{
    return json_decode(file_get_contents(config('ekotrip.import.catalogue_json')), true)['records'];
}

it('importe les 458 fiches du classeur en conservant leurs IDs', function () {
    expect(Fiche::count())->toBe(458)
        ->and(Fiche::where('is_active', true)->count())->toBe(383)
        ->and(Fiche::find('HEB-001')?->nom)->toBe('Villa Moina')
        ->and(Fiche::find('PRO-028'))->not->toBeNull();

    $activeIds = Fiche::where('is_active', true)->pluck('id')->sort()->values()->all();
    $jsonIds = collect(catalogueJson())->pluck('id')->sort()->values()->all();
    expect($activeIds)->toBe($jsonIds);
});

it('produit un rapport sans erreur et sans écart avec le JSON et le GeoJSON', function () {
    $run = ImportRun::query()->where('dry_run', false)->latest('id')->first();

    expect($run->status)->toBe('SUCCESS')
        ->and($run->summary['issues_by_severity']['ERROR'] ?? 0)->toBe(0)
        ->and($run->summary['counters']['json_field_mismatches'])->toBe(0)
        ->and($run->summary['counters']['geojson_features'])->toBe(361)
        ->and($run->issues()->whereIn('code', ['JSON_ID_MISMATCH', 'JSON_FIELD_MISMATCH', 'GEOJSON_MISMATCH'])->count())->toBe(0);
});

it('est idempotent : un second import ne modifie aucune fiche', function () {
    $run = app(CatalogueImporter::class)->run();

    expect($run->status)->toBe('SUCCESS')
        ->and($run->summary['counters']['fiches_unchanged'])->toBe(458)
        ->and($run->summary['counters']['fiches_created'] ?? 0)->toBe(0)
        ->and($run->summary['counters']['fiches_updated'] ?? 0)->toBe(0);
});

it("ne crée jamais de coordonnées pour une fiche qui n'en a pas", function () {
    $withoutGps = collect(catalogueJson())->whereNull('latitude')->pluck('id');

    expect($withoutGps)->not->toBeEmpty()
        ->and(Fiche::whereIn('id', $withoutGps)->whereNotNull('latitude')->count())->toBe(0)
        ->and(Fiche::whereIn('id', $withoutGps)->whereNotNull('longitude')->count())->toBe(0);
});

it('neutralise les placeholders techniques sans perdre la valeur brute', function () {
    foreach (config('ekotrip.placeholder_fields') as $field) {
        expect(Fiche::query()->public()->where($field, 'À CONFIRMER')->count())->toBe(0, "{$field} expose À CONFIRMER");
    }

    $fiche = Fiche::query()->whereNotNull('raw_placeholders')->where('is_active', true)->first();
    expect($fiche)->not->toBeNull()
        ->and(collect($fiche->raw_placeholders)->every(fn ($v, $field) => $fiche->{$field} === null))->toBeTrue();
});

it('applique les règles de publication', function () {
    expect(Fiche::find('HEB-001')->publication_status)->toBe('published')
        ->and(Fiche::find('HEB-005')->publication_status)->toBe('pending_review')   // À CONTRÔLER
        ->and(Fiche::find('HEB-004')->publication_status)->toBe('unpublished')      // À RESTRUCTURER
        ->and(Fiche::find('HEB-019')->publication_status)->toBe('archived')         // NE PAS PUBLIER
        ->and(Fiche::where('is_active', false)->where('publication_status', 'published')->count())->toBe(0);
});

it("n'utilise jamais A/A+ legacy pour le badge éco", function () {
    expect(Fiche::whereIn('niveau_ekotrip', ['A', 'A+'])->count())->toBeGreaterThan(0)
        ->and(Fiche::whereNotNull('eco_badge_public')->count())->toBe(0);
});

it('relie chaque étape de circuit à sa fiche maître et écarte HEB-019', function () {
    expect(CircuitStep::count())->toBe(84)
        ->and(CircuitStep::whereNull('fiche_id')->count())->toBe(0)
        ->and(CircuitStep::where('fiche_id', 'HEB-019')->first()->is_retained)->toBeFalse()
        ->and(CircuitStep::query()->servable()->count())->toBe(83);
});

it('ne persiste rien en mode --dry-run mais conserve le rapport', function () {
    DB::table('fiches')->where('id', 'HEB-001')->update(['nom' => 'Modifié']);

    $run = app(CatalogueImporter::class)->run(dryRun: true);

    expect($run->dry_run)->toBeTrue()
        ->and($run->status)->toBe('SUCCESS')
        ->and(ImportRun::find($run->id))->not->toBeNull()
        ->and(Fiche::find('HEB-001')->nom)->toBe('Modifié');
});
