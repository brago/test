<?php

use App\Filament\Resources\Fiches\Pages\ListFiches;
use App\Models\Fiche;
use App\Models\User;
use App\Services\Import\CatalogueImporter;
use Livewire\Livewire;

// Décisions de recette phase 1 du 15/10/2026.

it('décision 1 — À RECLASSER est à valider, ARCHIVER… est archivé', function () {
    expect(Fiche::whereIn('publication', ['À RECLASSER'])->pluck('publication_status')->unique()->all())->toBe(['pending_review'])
        ->and(Fiche::where('publication', 'like', 'ARCHIVER /%')->pluck('publication_status')->unique()->all())->toBe(['archived']);
});

it('décision 1 — un contenu éditorial est public mais jamais sur la carte', function () {
    foreach (['ACT-007', 'ACT-063'] as $id) {
        Fiche::find($id)->forceFill(['show_on_map' => true, 'latitude' => 14.6, 'longitude' => -61.0, 'content_type' => 'POINT', 'precision_geo' => 'Point plage'])->save();
    }

    $map = collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id');
    $list = collect($this->getJson('/api/v1/fiches?per_page=100&categories[]=activites')->json('data'))->pluck('id');

    expect($map)->not->toContain('ACT-007', 'ACT-063')
        ->and($list)->toContain('ACT-007', 'ACT-063')
        ->and(Fiche::find('ACT-007')->isOnMap())->toBeFalse();
    $this->getJson('/api/v1/fiches/ACT-063')->assertOk()->assertJsonPath('data.location.on_map', false);
});

it('décision 1 — un statut inconnu rend la fiche non publique et est signalé', function () {
    config(['ekotrip.publication_map' => collect(config('ekotrip.publication_map'))->except('PUBLIABLE')->all()]);

    $run = app(CatalogueImporter::class)->run(dryRun: true);

    expect($run->summary['counters']['fiches_public'])->toBe(2) // seuls les 2 contenus éditoriaux
        ->and($run->summary['issues_by_code']['UNKNOWN_PUBLICATION'])->toBe(381);
});

it('décision 2 — les fiches « À déterminer » sont hors carte, en liste et recherche, et « à géocoder »', function () {
    $ids = ['GAS-003', 'GAS-009', 'GAS-013', 'GAS-016'];

    expect(Fiche::query()->toGeocode()->pluck('id')->sort()->values()->all())->toBe($ids);

    $map = collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id');
    $list = collect($this->getJson('/api/v1/fiches?per_page=100&categories[]=gastronomie')->json('data'))->pluck('id');
    expect($map)->not->toContain(...$ids)->and($list)->toContain(...$ids);
    expect(collect($this->getJson('/api/v1/fiches?q=Petibonum')->json('data'))->pluck('id'))->toContain('GAS-003');

    $admin = User::factory()->create();
    $admin->forceFill(['is_admin' => true])->save();
    $this->actingAs($admin);
    Livewire::test(ListFiches::class)
        ->filterTable('a_geocoder')
        ->assertCanSeeTableRecords(Fiche::whereIn('id', $ids)->get())
        ->assertCountTableRecords(4);
});

it('décision 4 — HEB-019 est « à remplacer » et le circuit 4 reste continu sans elle', function () {
    $run = app(CatalogueImporter::class)->run(dryRun: true);
    expect($run->issues()->where('code', 'STEP_TO_REPLACE')->pluck('row_ref', 'message')->keys()->first())->toContain('HEB-019', 'à remplacer');

    $days = collect($this->getJson('/api/v1/circuits/CIR-004')->assertOk()->json('data.jours'));
    expect($days->pluck('jour')->all())->toBe([1, 2, 3]);
    foreach ($days as $day) {
        expect(collect($day['etapes'])->pluck('ordre')->all())->toBe(range(1, count($day['etapes'])));
    }
});

it('décision 5 — « Professionnels » est dans le groupe des Activités', function () {
    $groups = collect($this->getJson('/api/v1/config')->json('map_groups'))->keyBy('slug');

    expect($groups)->not->toHaveKey('professionnels')
        ->and($groups['bouger']['categories'])->toContain('activites', 'professionnels');

    $ids = collect($this->getJson('/api/v1/fiches?per_page=100&groups[]=bouger')->json('data'))->pluck('id');
    expect($ids->contains(fn ($id) => str_starts_with($id, 'PRO-')))->toBeTrue()
        ->and($ids->contains(fn ($id) => str_starts_with($id, 'ACT-')))->toBeTrue();
});

it('décision 8 — le rayon « Autour de moi » vaut 10 km par défaut et reste administrable', function () {
    $this->getJson('/api/v1/config')->assertJsonPath('nearby_radius_km', 10);

    \App\Models\Setting::find('nearby_radius_km')->update(['value' => 5]);
    $this->getJson('/api/v1/config')->assertJsonPath('nearby_radius_km', 5);
});
