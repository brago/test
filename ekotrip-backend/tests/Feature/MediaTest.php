<?php

use App\Models\Media;

function media(array $overrides = []): Media
{
    static $n = 0;
    $n++;

    return Media::create(array_merge([
        'id' => "MED-TEST-{$n}",
        'target_type' => 'FICHE',
        'target_id' => 'HEB-001',
        'media_role' => 'COVER',
        'source_type' => 'EKOTRIP_OWNED',
        'asset_url_or_ref' => "https://cdn.example.test/{$n}.jpg",
        'storage_mode' => 'EKOTRIP_STORAGE',
        'rights_status' => 'OWNED',
        'commercial_use_allowed' => true,
        'publication_status' => 'ACTIVE',
        'is_primary' => false,
    ], $overrides));
}

it('sert un média ACTIVE aux droits qualifiés', function () {
    $m = media(['is_primary' => true]);

    $this->getJson('/api/v1/fiches/HEB-001')->assertOk()
        ->assertJsonPath('data.cover.id', $m->id)
        ->assertJsonCount(1, 'data.media');
});

it('ne sert jamais un média TO_VERIFY, BLOCKED, non actif, expiré ou sans usage commercial', function (array $overrides) {
    media($overrides);

    $this->getJson('/api/v1/fiches/HEB-001')->assertOk()
        ->assertJsonPath('data.cover', null)
        ->assertJsonCount(0, 'data.media');
})->with([
    'droits à vérifier' => [['rights_status' => 'TO_VERIFY']],
    'droits bloqués' => [['rights_status' => 'BLOCKED']],
    'brouillon' => [['publication_status' => 'DRAFT']],
    'prêt mais non actif' => [['publication_status' => 'READY']],
    'bloqué' => [['publication_status' => 'BLOCKED']],
    'expiré' => [['expires_at' => now()->subDay()->toDateString()]],
    'usage commercial non autorisé' => [['commercial_use_allowed' => false]],
]);

it("refuse le stockage local d'une photo Google Places", function () {
    media(['source_type' => 'GOOGLE_PLACES', 'storage_mode' => 'EKOTRIP_STORAGE', 'rights_status' => 'PROVIDER_TERMS']);
})->throws(InvalidArgumentException::class);

it('respecte la priorité des sources : partenaire, puis ÉKO TRIP, licence ouverte, Google Places', function () {
    media(['id' => 'MED-G', 'source_type' => 'GOOGLE_PLACES', 'storage_mode' => 'DYNAMIC_PROVIDER', 'rights_status' => 'PROVIDER_TERMS', 'commercial_use_allowed' => false]);
    media(['id' => 'MED-O', 'source_type' => 'OPEN_LICENSE', 'storage_mode' => 'EXTERNAL_ASSET', 'rights_status' => 'LICENSE_VERIFIED', 'attribution_required' => true, 'attribution_text' => 'Auteur / CC BY']);
    media(['id' => 'MED-P', 'source_type' => 'PARTNER_UPLOAD', 'rights_status' => 'PARTNER_DECLARATION']);

    $response = $this->getJson('/api/v1/fiches/HEB-001')->assertOk();

    expect(collect($response->json('data.media'))->pluck('id')->all())->toBe(['MED-P', 'MED-O', 'MED-G'])
        ->and($response->json('data.cover.id'))->toBe('MED-P')
        ->and($response->json('data.media.1.attribution'))->toBe('Auteur / CC BY');
});

it('rejette une valeur hors énumération', function () {
    media(['target_type' => 'POI']);
})->throws(InvalidArgumentException::class);
