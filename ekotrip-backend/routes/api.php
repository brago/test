<?php

use App\Http\Controllers\Api\V1\CatalogueController;
use App\Http\Controllers\Api\V1\CircuitController;
use App\Http\Controllers\Api\V1\FicheController;
use App\Http\Controllers\Api\V1\FicheReportController;
use App\Http\Controllers\Api\V1\MapController;
use App\Http\Controllers\Api\V1\SearchController;
use Illuminate\Support\Facades\Route;

/*
| API publique v1 — lecture seule, sans authentification (phase 1).
| Toutes les réponses se limitent aux fiches publiques (Fiche::scopePublic).
*/
Route::prefix('v1')->middleware('throttle:api')->group(function () {
    Route::get('config', [CatalogueController::class, 'config']);
    Route::get('categories', [CatalogueController::class, 'categories']);
    Route::get('communes', [CatalogueController::class, 'communes']);

    Route::get('fiches', [FicheController::class, 'index']);
    Route::get('fiches/{id}', [FicheController::class, 'show'])->where('id', '[A-Za-z0-9\-]+');
    Route::post('fiches/{id}/reports', [FicheReportController::class, 'store'])
        ->where('id', '[A-Za-z0-9\-]+')
        ->middleware('throttle:fiche-reports');

    Route::get('map/fiches', [MapController::class, 'fiches']);
    Route::get('search/suggest', [SearchController::class, 'suggest']);

    Route::get('circuits', [CircuitController::class, 'index']);
    Route::get('circuits/{id}', [CircuitController::class, 'show'])->where('id', '[A-Za-z0-9\-]+');
});
