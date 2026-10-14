<?php

use App\Services\Catalogue\EcoBadgeResolver;

it('dérive le niveau public du référentiel 07_REF_SCORE', function (float $score, ?string $level) {
    expect(EcoBadgeResolver::levelFor($score))->toBe($level);
})->with([
    [0, null], [39.9, null], [40, 'Éko Engagé'], [59, 'Éko Engagé'],
    [60, 'Éko Responsable'], [79, 'Éko Responsable'], [80, 'Éko Exemplaire'], [100, 'Éko Exemplaire'],
]);
