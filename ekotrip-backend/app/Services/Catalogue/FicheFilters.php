<?php

namespace App\Services\Catalogue;

use Illuminate\Http\Request;

/**
 * Filtres communs à la liste et à la carte (Lot 1.5 §7 : mêmes résultats carte / liste).
 */
final class FicheFilters
{
    /**
     * @param  list<string>  $categories  slugs de catégories
     * @param  list<string>  $groups  slugs de groupes carte
     * @param  list<string>  $communes
     * @param  array{0:float,1:float}|null  $near  [lat, lng]
     * @param  array{0:float,1:float,2:float,3:float}|null  $bbox  [minLng, minLat, maxLng, maxLat]
     */
    public function __construct(
        public readonly ?string $q = null,
        public readonly array $categories = [],
        public readonly array $groups = [],
        public readonly array $communes = [],
        public readonly ?array $near = null,
        public readonly ?float $radiusKm = null,
        public readonly ?array $bbox = null,
        public readonly bool $ecoValidated = false,
    ) {}

    /** Construit depuis une requête déjà validée (FicheFilterRequest). */
    public static function fromRequest(Request $request): self
    {
        $near = $request->filled('near') ? array_map('floatval', explode(',', $request->string('near'))) : null;
        $bbox = $request->filled('bbox') ? array_map('floatval', explode(',', $request->string('bbox'))) : null;

        return new self(
            q: $request->filled('q') ? trim((string) $request->input('q')) : null,
            categories: array_values((array) $request->input('categories', [])),
            groups: array_values((array) $request->input('groups', [])),
            communes: array_values((array) $request->input('communes', [])),
            near: $near,
            radiusKm: $request->filled('radius') ? (float) $request->input('radius') : null,
            bbox: $bbox,
            ecoValidated: $request->boolean('eco_validated'),
        );
    }

    /** Clé de cache stable. */
    public function cacheKey(): string
    {
        return md5(json_encode(get_object_vars($this)));
    }
}
