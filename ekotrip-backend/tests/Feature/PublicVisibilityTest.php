<?php

use App\Models\Fiche;

dataset('fiches non publiques', [
    'à restructurer' => ['HEB-004'],
    'à contrôler' => ['HEB-005'],
    'ne pas publier' => ['HEB-019'],
]);

it("n'expose jamais une fiche non publique dans la fiche détaillée", function (string $id) {
    $this->getJson("/api/v1/fiches/{$id}")->assertNotFound();
})->with('fiches non publiques');

it("n'expose jamais une fiche non publique dans la liste, la recherche, la carte ni les suggestions", function (string $id) {
    $fiche = Fiche::find($id);

    $list = collect($this->getJson('/api/v1/fiches?per_page=100&q='.urlencode($fiche->nom))->json('data'))->pluck('id');
    $map = collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id');
    $suggest = collect($this->getJson('/api/v1/search/suggest?q='.urlencode($fiche->nom))->json('data'))->pluck('id');

    expect($list)->not->toContain($id)
        ->and($map)->not->toContain($id)
        ->and($suggest)->not->toContain($id);
})->with('fiches non publiques');

it('retire immédiatement une fiche archivée en back-office de l’API publique', function () {
    expect(collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id'))->toContain('HEB-001');

    Fiche::find('HEB-001')->update(['publication_status' => 'archived']);

    expect(collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id'))->not->toContain('HEB-001');
    $this->getJson('/api/v1/fiches/HEB-001')->assertNotFound();
});

it('liste toutes les fiches publiques, y compris celles sans coordonnées', function () {
    $response = $this->getJson('/api/v1/fiches?per_page=100')->assertOk();

    expect($response->json('meta.total'))->toBe(383);
});
