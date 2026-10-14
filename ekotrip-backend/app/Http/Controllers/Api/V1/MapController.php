<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Presenters\FichePresenter;
use App\Http\Requests\FicheFilterRequest;
use App\Models\Fiche;
use App\Services\Catalogue\CatalogueCache;
use App\Services\Catalogue\FicheFilters;
use App\Services\Catalogue\FicheQuery;
use App\Services\Catalogue\MediaResolver;
use Illuminate\Http\JsonResponse;

class MapController extends Controller
{
    public function __construct(
        private readonly FicheQuery $query,
        private readonly MediaResolver $media,
    ) {}

    /**
     * GeoJSON des marqueurs : fiches publiques, show_on_map = TRUE, coordonnées valides,
     * jamais de point fabriqué. Mêmes filtres que /fiches.
     */
    public function fiches(FicheFilterRequest $request): JsonResponse
    {
        $filters = FicheFilters::fromRequest($request);

        $payload = CatalogueCache::remember('map:'.$filters->cacheKey(), function () use ($filters) {
            $fiches = $this->query->build($filters, mapOnly: true)->with('categories.mapGroup')->get();
            $media = $this->media->forTargets('FICHE', $fiches->pluck('id')->all());

            return [
                'type' => 'FeatureCollection',
                'features' => $fiches->map(function (Fiche $f) use ($media) {
                    $summary = FichePresenter::summary($f, $media->get($f->id));
                    unset($summary['location']['latitude'], $summary['location']['longitude']);

                    return [
                        'type' => 'Feature',
                        'id' => $f->id,
                        'geometry' => ['type' => 'Point', 'coordinates' => [$f->longitude, $f->latitude]],
                        'properties' => $summary,
                    ];
                })->values()->all(),
                'meta' => ['total' => $fiches->count()],
            ];
        });

        return response()->json($payload);
    }
}
