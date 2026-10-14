<?php

namespace App\Services\Catalogue;

use App\Models\PrecisionGeoRule;

/**
 * Dérive geo_display_type / label / navigation depuis precision_geo
 * (63_CARTO_ROUTAGE_V1 « INTERPRÉTATION precision_geo »).
 *
 * 1. Pas de coordonnées ou content_type sans point → NON_NAVIGABLE.
 * 2. Sinon, règle precision_geo → classe (table administrable precision_geo_rules).
 * 3. Libellé inconnu → classe la plus prudente (NON_NAVIGABLE) et signalement.
 */
class GeoClassifier
{
    /** @var array<string,string> */
    private array $rules;

    public function __construct(?array $rules = null)
    {
        $this->rules = $rules ?? PrecisionGeoRule::query()->pluck('geo_display_type', 'precision_geo')->all();
    }

    /**
     * @return array{type:string,label:string,navigation_allowed:bool,known_rule:bool}
     */
    public function classify(?string $precisionGeo, ?string $contentType, ?float $lat, ?float $lng): array
    {
        $knownRule = true;

        if ($lat === null || $lng === null
            || in_array(Text::normalize($contentType), config('ekotrip.geo.non_point_content_types'), true)) {
            $type = 'NON_NAVIGABLE';
        } elseif ($precisionGeo !== null && isset($this->rules[$precisionGeo])) {
            $type = $this->rules[$precisionGeo];
        } else {
            $type = config('ekotrip.geo.unknown_precision_fallback');
            $knownRule = false;
        }

        return [
            'type' => $type,
            'label' => config("ekotrip.geo.labels.{$type}"),
            'navigation_allowed' => $type !== 'NON_NAVIGABLE',
            'known_rule' => $knownRule,
        ];
    }
}
