<?php

use App\Services\Catalogue\GeoClassifier;

beforeEach(fn () => $this->geo = new GeoClassifier([
    'GPS exact – établissement' => 'EXACT',
    'Point départ randonnée' => 'ACCESS_OR_DEPARTURE',
    'Point plage' => 'INDICATIVE',
]));

it('applique la règle precision_geo pour un point avec coordonnées', function () {
    expect($this->geo->classify('Point départ randonnée', 'POINT', 14.6, -61.0))
        ->toMatchArray(['type' => 'ACCESS_OR_DEPARTURE', 'label' => "Point d'accès / départ", 'navigation_allowed' => true]);
});

it('rend NON_NAVIGABLE une fiche sans coordonnées', function () {
    expect($this->geo->classify('GPS exact – établissement', 'POINT', null, null)['type'])->toBe('NON_NAVIGABLE');
});

it('rend NON_NAVIGABLE un contenu sans point unique', function (string $contentType) {
    expect($this->geo->classify('GPS exact – établissement', $contentType, 14.6, -61.0))
        ->toMatchArray(['type' => 'NON_NAVIGABLE', 'navigation_allowed' => false]);
})->with(['SANS_POINT', 'MOBILE', 'EDITORIAL', 'ZONE_MULTI_POI']);

it('choisit la classe la plus prudente pour un libellé inconnu et le signale', function () {
    expect($this->geo->classify('Libellé jamais vu', 'POINT', 14.6, -61.0))
        ->toMatchArray(['type' => 'NON_NAVIGABLE', 'known_rule' => false]);
});
