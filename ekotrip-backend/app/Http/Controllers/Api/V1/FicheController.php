<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Presenters\FichePresenter;
use App\Http\Requests\FicheFilterRequest;
use App\Models\Fiche;
use App\Services\Catalogue\FicheFilters;
use App\Services\Catalogue\FicheQuery;
use App\Services\Catalogue\MediaResolver;
use Illuminate\Http\JsonResponse;

class FicheController extends Controller
{
    public function __construct(
        private readonly FicheQuery $query,
        private readonly MediaResolver $media,
    ) {}

    /** Liste paginée ; inclut les fiches publiques sans coordonnées (Lot 1.5 §7). */
    public function index(FicheFilterRequest $request): JsonResponse
    {
        $filters = FicheFilters::fromRequest($request);
        $perPage = (int) $request->input('per_page', config('ekotrip.api.per_page'));

        $page = $this->query->build($filters)
            ->with('categories.mapGroup')
            ->paginate($perPage)
            ->withQueryString();

        $media = $this->media->forTargets('FICHE', $page->getCollection()->pluck('id')->all());

        return response()->json([
            'data' => $page->getCollection()->map(fn (Fiche $f) => FichePresenter::summary($f, $media->get($f->id)))->values(),
            'meta' => [
                'total' => $page->total(),
                'per_page' => $page->perPage(),
                'current_page' => $page->currentPage(),
                'last_page' => $page->lastPage(),
            ],
            'links' => [
                'next' => $page->nextPageUrl(),
                'prev' => $page->previousPageUrl(),
            ],
        ]);
    }

    /** Fiche détaillée ; 404 si la fiche n'est pas publique (archivée, à contrôler…). */
    public function show(string $id): JsonResponse
    {
        $fiche = Fiche::query()->public()->with([
            'categories.mapGroup',
            'pois',
            'activityProLinks' => fn ($q) => $q->displayable()->with('pro.categories.mapGroup'),
            'proActivityLinks' => fn ($q) => $q->displayable()->with('activity.categories.mapGroup'),
            'circuitSteps' => fn ($q) => $q->where('is_retained', true)->with('circuit'),
        ])->findOrFail($id);

        $relatedIds = $fiche->activityProLinks->pluck('pro_fiche_id')
            ->merge($fiche->proActivityLinks->pluck('activity_fiche_id'))
            ->unique()->values()->all();

        return response()->json([
            'data' => FichePresenter::detail(
                $fiche,
                $this->media->forTargets('FICHE', [$fiche->id])->get($fiche->id, collect()),
                $this->media->forTargets('FICHE', $relatedIds),
            ),
        ]);
    }
}
