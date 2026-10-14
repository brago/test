<?php

namespace App\Services\Catalogue;

use App\Models\EcoEvaluation;
use App\Models\Fiche;

/**
 * Seule source de eco_badge_public (07_REF_SCORE, 77_GARDEFOUS_DONNEES_V11) :
 * évaluation VALIDATED non révoquée + au moins une preuve VALIDATED.
 * niveau_ekotrip (A/A+ legacy), score_ekotrip et les pré-évaluations ne sont jamais lus ici.
 */
class EcoBadgeResolver
{
    public function resolve(Fiche $fiche): ?string
    {
        /** @var EcoEvaluation|null $evaluation */
        $evaluation = $fiche->ecoEvaluations()
            ->where('status', 'VALIDATED')
            ->whereNull('revoked_at')
            ->whereHas('proofs', fn ($q) => $q->where('status', 'VALIDATED'))
            ->latest('validated_at')
            ->first();

        return $evaluation ? self::levelFor($evaluation->score) : null;
    }

    public function refresh(?Fiche $fiche): void
    {
        // Instance relue en base : une relation déjà chargée peut porter un badge périmé.
        $fiche = $fiche?->fresh();
        if (! $fiche) {
            return;
        }

        $badge = $this->resolve($fiche);

        if ($fiche->eco_badge_public !== $badge) {
            $fiche->forceFill(['eco_badge_public' => $badge])->saveQuietly();
            CatalogueCache::flush();
        }
    }

    public static function levelFor(float $score): ?string
    {
        foreach (config('ekotrip.eco_levels') as $level) {
            if ($score >= $level['min']) {
                return $level['label'];
            }
        }

        return null;
    }
}
