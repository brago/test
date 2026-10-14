<?php

use App\Models\Fiche;
use App\Models\FicheReport;

it('enregistre un signalement sans modifier la fiche', function () {
    $before = Fiche::find('HEB-001')->toArray();

    $this->postJson('/api/v1/fiches/HEB-001/reports', ['motif' => 'TELEPHONE', 'commentaire' => 'Numéro injoignable'])
        ->assertCreated()
        ->assertJsonPath('data.statut', 'NOUVEAU');

    expect(FicheReport::where('fiche_id', 'HEB-001')->count())->toBe(1)
        ->and(Fiche::find('HEB-001')->toArray())->toBe($before);
});

it('refuse un ID inexistant ou une fiche non publique', function (string $id) {
    $this->postJson("/api/v1/fiches/{$id}/reports", ['motif' => 'AUTRE'])->assertNotFound();
})->with(['HEB-999', 'HEB-019']);

it('refuse un motif hors liste contrôlée', function () {
    $this->postJson('/api/v1/fiches/HEB-001/reports', ['motif' => 'SPAM'])->assertUnprocessable();
});

it('limite le débit des signalements', function () {
    foreach (range(1, config('ekotrip.reports.per_minute')) as $i) {
        $this->postJson('/api/v1/fiches/HEB-001/reports', ['motif' => 'AUTRE'])->assertCreated();
    }

    $this->postJson('/api/v1/fiches/HEB-001/reports', ['motif' => 'AUTRE'])->assertTooManyRequests();
});
