<?php

use App\Models\Fiche;

const INTERNAL_FIELDS = [
    'score_ekotrip', 'niveau_ekotrip', 'statut_evaluation', 'id_activites_associees',
    'activites_associees', 'source_principale', 'source_geolocalisation', 'raw_placeholders',
    'tags_ia', 'note_dev', 'note_interne', 'date_derniere_verification', 'source_derniere_verification',
    'statut_verification', 'publication', 'publication_status', 'is_active', 'search_categories',
];

function collectKeys(array $data): array
{
    $keys = [];
    foreach ($data as $key => $value) {
        if (is_string($key)) {
            $keys[] = $key;
        }
        if (is_array($value)) {
            $keys = array_merge($keys, collectKeys($value));
        }
    }

    return array_unique($keys);
}

it("n'expose aucun champ interne", function (string $url) {
    $keys = collectKeys($this->getJson($url)->assertOk()->json());

    expect(array_intersect($keys, INTERNAL_FIELDS))->toBe([]);
})->with([
    'liste' => '/api/v1/fiches?per_page=50',
    'détail pro' => '/api/v1/fiches/PRO-001',
    'détail activité' => '/api/v1/fiches/ACT-074',
    'carte' => '/api/v1/map/fiches',
    'circuit' => '/api/v1/circuits/CIR-001',
]);

it('conserve null pour une information non renseignée', function () {
    $fiche = Fiche::query()->public()->whereNull('horaires')->whereNull('tarif_indicatif')->first();

    $this->getJson("/api/v1/fiches/{$fiche->id}")->assertOk()
        ->assertJsonPath('data.infos_pratiques.horaires', null)
        ->assertJsonPath('data.infos_pratiques.tarif_indicatif', null);
});

it("n'expose jamais « À CONFIRMER »", function () {
    $fiche = Fiche::query()->public()->whereNotNull('raw_placeholders')->first();
    $data = $this->getJson("/api/v1/fiches/{$fiche->id}")->assertOk()->json();

    expect(mb_strtoupper(json_encode($data, JSON_UNESCAPED_UNICODE)))->not->toContain('À CONFIRMER');
});

it('masque les actions sans donnée et ne propose jamais de réservation fictive', function () {
    $fiche = Fiche::query()->public()->whereNull('telephone')->whereNull('site_internet')->first();

    $this->getJson("/api/v1/fiches/{$fiche->id}")->assertOk()
        ->assertJsonPath('data.actions.appeler', false)
        ->assertJsonPath('data.actions.site', false)
        ->assertJsonPath('data.actions.reserver', false);
});

it("n'expose que les liens pro ↔ activité affichables entre fiches publiques", function () {
    // PRO-002 → ACT-026 est marqué « Afficher » mais ACT-026 est inactive.
    $offers = collect($this->getJson('/api/v1/fiches/PRO-002')->assertOk()->json('data.activites_proposees'))->pluck('id');

    expect($offers)->not->toContain('ACT-026');
});

it('valide les paramètres de filtre', function (string $query) {
    $this->getJson("/api/v1/fiches?{$query}")->assertUnprocessable();
})->with(['near=abc', 'bbox=1,2,3', 'radius=1000&near=14.6,-61', 'per_page=1000', 'communes[]=Sainte-Anne']);

it('expose la configuration, les catégories et les communes', function () {
    $this->getJson('/api/v1/config')->assertOk()
        ->assertJsonCount(4, 'geo_display_types')
        ->assertJsonPath('navigation', ['explorer', 'decouvrir', 'adresses_locales', 'mon_ekotrip', 'profil']);

    $categories = collect($this->getJson('/api/v1/categories')->assertOk()->json('data'));
    expect($categories)->toHaveCount(8)
        ->and($categories->sum('fiches_count'))->toBe(383)
        ->and($categories->every(fn ($c) => $c['groupe'] !== null))->toBeTrue();

    $communes = collect($this->getJson('/api/v1/communes')->assertOk()->json('data'));
    expect($communes)->toHaveCount(34)
        ->and($communes->firstWhere('code_insee', '97231')['nom'])->toBe('Les Trois-Îlets');
});
