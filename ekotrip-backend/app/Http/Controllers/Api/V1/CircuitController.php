<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Presenters\FichePresenter;
use App\Models\Circuit;
use App\Models\CircuitNote;
use App\Models\CircuitStep;
use App\Services\Catalogue\MediaResolver;
use Illuminate\Http\JsonResponse;

/**
 * Circuits éditoriaux (05A / 05 / 05D). Aucune distance, durée ou tracé n'est calculé
 * ici : seules les valeurs validées de la source sont exposées (null sinon).
 */
class CircuitController extends Controller
{
    public function __construct(private readonly MediaResolver $media) {}

    public function index(): JsonResponse
    {
        $circuits = Circuit::query()->orderBy('sort_order')->get();
        $media = $this->media->forTargets('CIRCUIT', $circuits->pluck('id')->all());

        return response()->json(['data' => $circuits->map(fn (Circuit $c) => $this->summary($c, $media->get($c->id)))->values()]);
    }

    public function show(string $id): JsonResponse
    {
        $circuit = Circuit::query()->findOrFail($id);

        $steps = $circuit->steps()->servable()->with('fiche.categories.mapGroup')->get();
        $notes = $circuit->notes()->with('fiche.categories.mapGroup')->get();
        $accommodations = $notes->where('kind', CircuitNote::KIND_HEBERGEMENT)->filter(fn (CircuitNote $n) => $n->fiche?->isPublic());

        $ficheIds = $steps->pluck('fiche_id')->merge($accommodations->pluck('fiche_id'))->unique()->values()->all();
        $ficheMedia = $this->media->forTargets('FICHE', $ficheIds);

        return response()->json(['data' => $this->summary($circuit, $this->media->forTargets('CIRCUIT', [$circuit->id])->get($circuit->id)) + [
            'jours' => $steps->groupBy('day')->map(fn ($daySteps, $day) => [
                'jour' => (int) $day,
                'etapes' => $daySteps->values()->map(fn (CircuitStep $s, $i) => [
                    'ordre' => $i + 1,
                    'label' => $s->label,
                    'commune' => $s->commune,
                    'a_encadrer' => str_contains((string) $s->decision, 'PRÉCAUTION'),
                    'fiche' => FichePresenter::summary($s->fiche, $ficheMedia->get($s->fiche_id)),
                ])->all(),
            ])->values()->all(),
            'precautions' => $notes->where('kind', CircuitNote::KIND_PRECAUTION)->pluck('text')->values()->all(),
            'hebergements' => $accommodations->map(fn (CircuitNote $n) => FichePresenter::summary($n->fiche, $ficheMedia->get($n->fiche_id)))->values()->all(),
        ]]);
    }

    private function summary(Circuit $c, $media): array
    {
        return [
            'id' => $c->id,
            'nom' => $c->nom,
            'resume' => $c->resume,
            'duree_totale' => $c->duree_totale,
            'nb_jours' => $c->nb_jours,
            'distance_totale' => $c->distance_totale,
            'difficulte' => $c->difficulte,
            'mode_deplacement' => $c->mode_deplacement,
            'point_depart' => $c->point_depart,
            'communes' => $c->communes,
            'cover' => MediaResolver::cover($media),
        ];
    }
}
