<?php

use App\Models\Fiche;

it('liste les 4 circuits sans distance ni durée inventées', function () {
    $circuits = collect($this->getJson('/api/v1/circuits')->assertOk()->json('data'));

    expect($circuits->pluck('id')->all())->toBe(['CIR-001', 'CIR-002', 'CIR-003', 'CIR-004'])
        ->and($circuits->pluck('distance_totale')->filter()->all())->toBe([]);
});

it('ne sert que des étapes retenues reliées à des fiches publiques', function () {
    $data = $this->getJson('/api/v1/circuits/CIR-004')->assertOk()->json('data');
    $fiches = collect($data['jours'])->flatMap(fn ($j) => collect($j['etapes'])->pluck('fiche.id'));

    expect($fiches)->not->toContain('HEB-019')
        ->and($fiches)->toHaveCount(20)
        ->and(Fiche::whereIn('id', $fiches)->get()->every(fn (Fiche $f) => $f->isPublic()))->toBeTrue();
});

it('sépare précautions et options d’hébergement des étapes', function () {
    $data = $this->getJson('/api/v1/circuits/CIR-001')->assertOk()->json('data');

    expect($data['precautions'])->toContain('Rocher du Diamant en plongée : réservé aux plongeurs expérimentés, courants parfois violents.')
        ->and(collect($data['hebergements'])->pluck('id'))->toContain('HEB-061');
});

it('renvoie 404 pour un circuit inconnu', function () {
    $this->getJson('/api/v1/circuits/CIR-999')->assertNotFound();
});
