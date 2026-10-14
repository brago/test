<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\FicheReportRequest;
use App\Models\Fiche;
use App\Models\FicheReport;
use Illuminate\Http\JsonResponse;

/**
 * Signalement d'une information incorrecte (62_EXTENSIONS_SCHEMA).
 * Ne modifie jamais la fiche : crée une entrée NOUVEAU dans la file du back-office.
 */
class FicheReportController extends Controller
{
    public function store(FicheReportRequest $request, string $id): JsonResponse
    {
        $fiche = Fiche::query()->public()->findOrFail($id);

        $report = FicheReport::query()->create([
            'fiche_id' => $fiche->id,
            'user_id' => $request->user()?->id,
            'motif' => $request->validated('motif'),
            'commentaire' => $request->validated('commentaire'),
            'statut' => 'NOUVEAU',
            'ip_hash' => hash_hmac('sha256', (string) $request->ip(), (string) config('app.key')),
        ]);

        return response()->json(['data' => ['id' => $report->id, 'statut' => $report->statut]], 201);
    }
}
