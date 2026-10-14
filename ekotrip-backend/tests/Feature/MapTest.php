<?php

use App\Models\Fiche;

it('ne place sur la carte que des fiches publiques, cartographiables, avec coordonnées valides', function () {
    $features = collect($this->getJson('/api/v1/map/fiches')->assertOk()->json('features'));

    expect($features)->toHaveCount(357);

    $fiches = Fiche::whereIn('id', $features->pluck('id'))->get()->keyBy('id');
    foreach ($features as $feature) {
        $fiche = $fiches[$feature['id']];
        expect($fiche->isPublic())->toBeTrue()
            ->and($fiche->show_on_map)->toBeTrue()
            ->and($feature['geometry']['coordinates'])->toBe([$fiche->longitude, $fiche->latitude])
            ->and($feature['properties']['location']['geo_display_type'])->not->toBe('NON_NAVIGABLE');
    }
});

it('retire de la carte les fiches NON_NAVIGABLE (« À déterminer »)', function () {
    $ids = collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id');

    expect($ids)->not->toContain('GAS-003', 'GAS-009', 'GAS-013', 'GAS-016');
    // Elles restent consultables en fiche, sans itinéraire.
    $this->getJson('/api/v1/fiches/GAS-003')->assertOk()
        ->assertJsonPath('data.location.navigation_allowed', false)
        ->assertJsonPath('data.actions.itineraire', false);
});

it('peut afficher les fiches NON_NAVIGABLE sans itinéraire si la configuration le demande', function () {
    config(['ekotrip.map.include_non_navigable' => true]);

    $features = collect($this->getJson('/api/v1/map/fiches')->json('features'))->keyBy('id');

    expect($features)->toHaveCount(361)
        ->and($features['GAS-003']['properties']['location']['navigation_allowed'])->toBeFalse();
});

it('respecte les quatre classes de précision géographique', function (string $id, string $type, string $label, bool $navigation) {
    $this->getJson("/api/v1/fiches/{$id}")->assertOk()
        ->assertJsonPath('data.location.geo_display_type', $type)
        ->assertJsonPath('data.location.geo_display_label', $label)
        ->assertJsonPath('data.location.navigation_allowed', $navigation)
        ->assertJsonPath('data.actions.itineraire', $navigation);
})->with([
    'EXACT' => ['HEB-001', 'EXACT', 'Localisation précise', true],
    'ACCESS_OR_DEPARTURE' => ['NAT-002', 'ACCESS_OR_DEPARTURE', "Point d'accès / départ", true],
    'INDICATIVE' => [fn () => Fiche::query()->public()->where('geo_display_type', 'INDICATIVE')->value('id'), 'INDICATIVE', 'Localisation indicative', true],
    'NON_NAVIGABLE (mobile)' => ['PRO-028', 'NON_NAVIGABLE', 'Localisation non confirmée', false],
]);

it('donne les mêmes fiches sur la carte et dans la liste pour les mêmes filtres', function () {
    $query = 'categories[]=nature&communes[]=97202'; // Les Anses-d'Arlet

    $list = collect($this->getJson("/api/v1/fiches?per_page=100&{$query}")->json('data'));
    $map = collect($this->getJson("/api/v1/map/fiches?{$query}")->json('features'))->pluck('id')->sort()->values();

    expect($map)->not->toBeEmpty()
        ->and($list->where('location.on_map', true)->pluck('id')->sort()->values()->all())->toBe($map->all());
});

it('filtre autour de moi par rayon sans inventer de temps de trajet', function () {
    $fiche = Fiche::find('HEB-001');
    $response = $this->getJson("/api/v1/fiches?near={$fiche->latitude},{$fiche->longitude}&radius=2")->assertOk();

    $data = collect($response->json('data'));
    expect($data->first()['id'])->toBe('HEB-001')
        ->and($data->every(fn ($f) => $f['distance_km'] <= 2))->toBeTrue()
        ->and($data->first())->not->toHaveKey('duree_trajet');
});

it('filtre par emprise (bbox)', function () {
    $features = collect($this->getJson('/api/v1/map/fiches?bbox=-61.25,14.6,-61.0,14.75')->json('features'));

    expect($features)->not->toBeEmpty()
        ->and($features->every(fn ($f) => $f['geometry']['coordinates'][1] >= 14.6 && $f['geometry']['coordinates'][1] <= 14.75))->toBeTrue();
});
