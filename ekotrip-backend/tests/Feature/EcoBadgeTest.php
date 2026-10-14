<?php

use App\Models\EcoEvaluation;
use App\Models\Fiche;

function validatedEvaluation(Fiche $fiche, float $score, string $proofStatus = 'VALIDATED'): EcoEvaluation
{
    $evaluation = EcoEvaluation::create([
        'fiche_id' => $fiche->id, 'score' => $score, 'status' => 'VALIDATED', 'validated_at' => now(),
    ]);
    $evaluation->proofs()->create(['kind' => 'LABEL', 'reference' => 'Label test', 'status' => $proofStatus]);

    return $evaluation;
}

it("n'affiche aucun badge pour une valeur legacy A/A+", function () {
    $fiche = Fiche::query()->public()->where('niveau_ekotrip', 'A+')->first();

    $this->getJson("/api/v1/fiches/{$fiche->id}")->assertOk()->assertJsonPath('data.eco_badge', null);
});

it("n'affiche aucun badge pour une pré-évaluation, même avec un score", function () {
    $fiche = Fiche::query()->public()->where('statut_evaluation', 'like', 'Pré-évaluation%')->whereNotNull('score_ekotrip')->first()
        ?? Fiche::query()->public()->where('statut_evaluation', 'like', 'Pré-évaluation%')->first();

    $this->getJson("/api/v1/fiches/{$fiche->id}")->assertOk()->assertJsonPath('data.eco_badge', null);
});

it('exige une évaluation validée ET une preuve validée', function () {
    $fiche = Fiche::find('HEB-002');

    EcoEvaluation::create(['fiche_id' => $fiche->id, 'score' => 85, 'status' => 'DRAFT']);
    expect($fiche->refresh()->eco_badge_public)->toBeNull();

    validatedEvaluation($fiche, 85, proofStatus: 'PENDING');
    expect($fiche->refresh()->eco_badge_public)->toBeNull();

    validatedEvaluation($fiche, 65);
    expect($fiche->refresh()->eco_badge_public)->toBe('Éko Responsable');
    $this->getJson('/api/v1/fiches/HEB-002')->assertJsonPath('data.eco_badge', 'Éko Responsable');
});

it('retire le badge quand l’évaluation est révoquée', function () {
    $fiche = Fiche::find('HEB-002');
    $evaluation = validatedEvaluation($fiche, 90);
    expect($fiche->refresh()->eco_badge_public)->toBe('Éko Exemplaire');

    $evaluation->update(['status' => 'REVOKED', 'revoked_at' => now()]);
    expect($fiche->refresh()->eco_badge_public)->toBeNull();
});

it('ne laisse pas un plan commercial ou un statut partenaire influencer le badge', function () {
    $fiche = Fiche::find('HEB-002');
    $fiche->update(['statut_partenaire' => 'PREMIUM', 'niveau_ekotrip' => 'A+', 'score_ekotrip' => 99]);

    expect($fiche->refresh()->eco_badge_public)->toBeNull();
});

it('filtre « ÉKO TRIP validé » sur les seuls badges réellement validés', function () {
    expect($this->getJson('/api/v1/fiches?eco_validated=1')->json('meta.total'))->toBe(0);

    validatedEvaluation(Fiche::find('HEB-002'), 45);

    $ids = collect($this->getJson('/api/v1/fiches?eco_validated=1')->json('data'))->pluck('id');
    expect($ids->all())->toBe(['HEB-002']);
});
