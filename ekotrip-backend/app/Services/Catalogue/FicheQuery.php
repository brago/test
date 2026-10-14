<?php

namespace App\Services\Catalogue;

use App\Models\Fiche;
use App\Models\Setting;
use App\Services\Search\SearchEngine;
use Illuminate\Database\Eloquent\Builder;

/**
 * Construit LA requête du catalogue public. Utilisée par la liste, la carte
 * et la recherche : garantit que les mêmes filtres donnent les mêmes fiches.
 */
class FicheQuery
{
    public function __construct(private readonly SearchEngine $search) {}

    public function build(FicheFilters $filters, bool $mapOnly = false): Builder
    {
        $query = $mapOnly ? Fiche::query()->onMap() : Fiche::query()->public();
        $query->select('fiches.*');

        if ($filters->categories) {
            $query->whereHas('categories', fn (Builder $q) => $q->whereIn('categories.slug', $filters->categories));
        }

        if ($filters->groups) {
            $query->whereHas('categories.mapGroup', fn (Builder $q) => $q->whereIn('map_groups.slug', $filters->groups));
        }

        if ($filters->communes) {
            $query->whereIn('fiches.commune', $filters->communes);
        }

        if ($filters->ecoValidated) {
            $query->whereNotNull('fiches.eco_badge_public');
        }

        if ($filters->bbox) {
            [$minLng, $minLat, $maxLng, $maxLat] = $filters->bbox;
            $query->whereBetween('fiches.latitude', [$minLat, $maxLat])
                ->whereBetween('fiches.longitude', [$minLng, $maxLng]);
        }

        if ($filters->near) {
            [$lat, $lng] = $filters->near;
            $radius = $filters->radiusKm ?? (float) Setting::get('nearby_radius_km');
            // Distance à vol d'oiseau (haversine) : filtre de proximité, jamais présentée comme temps de trajet.
            $distance = '(6371 * ACOS(LEAST(1, COS(RADIANS(?)) * COS(RADIANS(fiches.latitude)) * COS(RADIANS(fiches.longitude) - RADIANS(?)) + SIN(RADIANS(?)) * SIN(RADIANS(fiches.latitude)))))';
            $query->whereNotNull('fiches.latitude')
                ->selectRaw("{$distance} AS distance_km", [$lat, $lng, $lat])
                ->whereRaw("{$distance} <= ?", [$lat, $lng, $lat, $radius]);
        }

        if ($filters->q !== null && $filters->q !== '') {
            return $this->search->search($query, $filters->q);
        }

        return $filters->near
            ? $query->orderBy('distance_km')->orderBy('fiches.nom')
            : $query->orderBy('fiches.nom');
    }
}
