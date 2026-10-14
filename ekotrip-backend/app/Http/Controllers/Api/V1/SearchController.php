<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Services\Search\SearchEngine;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class SearchController extends Controller
{
    public function suggest(Request $request, SearchEngine $search): JsonResponse
    {
        $validated = $request->validate([
            'q' => ['required', 'string', 'max:100'],
            'limit' => ['nullable', 'integer', 'min:1', 'max:20'],
        ]);

        return response()->json([
            'data' => $search->suggest($validated['q'], (int) ($validated['limit'] ?? config('ekotrip.search.suggest_limit'))),
        ]);
    }
}
