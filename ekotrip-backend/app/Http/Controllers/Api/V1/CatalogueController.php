<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\Fiche;
use App\Models\MapGroup;
use App\Models\Setting;
use App\Services\Catalogue\CatalogueCache;
use Illuminate\Http\JsonResponse;

class CatalogueController extends Controller
{
    public function config(): JsonResponse
    {
        return response()->json(CatalogueCache::remember('config', fn () => [
            'navigation' => ['explorer', 'decouvrir', 'adresses_locales', 'mon_ekotrip', 'profil'],
            'map_groups' => MapGroup::query()->with('categories')->orderBy('sort_order')->get()->map(fn (MapGroup $g) => [
                'slug' => $g->slug,
                'label' => $g->label,
                'icon' => $g->icon,
                'color' => $g->color,
                'categories' => $g->categories->sortBy('sort_order')->pluck('slug')->values(),
            ]),
            'geo_display_types' => collect(config('ekotrip.geo.labels'))->map(fn ($label, $type) => [
                'type' => $type,
                'label' => $label,
                'navigation_allowed' => $type !== 'NON_NAVIGABLE',
            ])->values(),
            'nearby_radius_km' => (float) Setting::get('nearby_radius_km'),
            'search' => ['suggest_min_chars' => (int) config('ekotrip.search.suggest_min_chars')],
        ]));
    }

    public function categories(): JsonResponse
    {
        return response()->json(['data' => CatalogueCache::remember('categories', fn () => Category::query()
            ->with('mapGroup')
            ->withCount(['fiches as fiches_count' => fn ($q) => $q->public()])
            ->orderBy('sort_order')
            ->get()
            ->map(fn (Category $c) => [
                'nom' => $c->name,
                'slug' => $c->slug,
                'ordre' => $c->sort_order,
                'groupe' => $c->mapGroup?->slug,
                'fiches_count' => $c->fiches_count,
            ])->values()->all())]);
    }

    public function communes(): JsonResponse
    {
        return response()->json(['data' => CatalogueCache::remember('communes', fn () => Fiche::query()->public()
            ->whereNotNull('commune')
            ->selectRaw('commune, COUNT(*) AS fiches_count')
            ->groupBy('commune')
            ->orderBy('commune')
            ->get()
            ->map(fn ($row) => ['nom' => $row->commune, 'fiches_count' => (int) $row->fiches_count])
            ->values()->all())]);
    }
}
