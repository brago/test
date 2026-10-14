<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\Commune;
use App\Models\Fiche;
use App\Models\MapGroup;
use App\Models\Setting;
use App\Services\Catalogue\CatalogueCache;
use App\Services\Catalogue\CommuneDirectory;
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

    /**
     * Les 34 communes officielles et le nombre de fiches publiques rattachées
     * (une fiche multi-communes compte dans chacune).
     */
    public function communes(CommuneDirectory $directory): JsonResponse
    {
        return response()->json(['data' => CatalogueCache::remember('communes', function () use ($directory) {
            $counts = [];
            $rows = Fiche::query()->public()->whereNotNull('commune')
                ->selectRaw('commune, COUNT(*) AS n')->groupBy('commune')->get();
            foreach ($rows as $row) {
                foreach ($directory->forRaw($row->commune)['communes'] ?? [] as $commune) {
                    $counts[$commune['code_insee']] = ($counts[$commune['code_insee']] ?? 0) + (int) $row->n;
                }
            }

            return Commune::query()->orderBy('nom')->get()->map(fn (Commune $c) => [
                'code_insee' => $c->code_insee,
                'nom' => $c->nom,
                'fiches_count' => $counts[$c->code_insee] ?? 0,
            ])->values()->all();
        })]);
    }
}
