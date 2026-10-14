<?php

namespace App\Providers;

use App\Services\Search\MysqlSearchEngine;
use App\Services\Search\SearchEngine;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;
use InvalidArgumentException;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        // Moteur de recherche interchangeable (MySQL en V1, Meilisearch plus tard).
        $this->app->singleton(SearchEngine::class, fn ($app) => match (config('ekotrip.search.engine')) {
            'mysql' => $app->make(MysqlSearchEngine::class),
            default => throw new InvalidArgumentException('Moteur de recherche inconnu : '.config('ekotrip.search.engine')),
        });
    }

    public function boot(): void
    {
        RateLimiter::for('api', fn (Request $request) => Limit::perMinute(120)->by($request->ip()));

        // Anti-spam des signalements (62_EXTENSIONS_SCHEMA : « Limiter et journaliser »).
        RateLimiter::for('fiche-reports', fn (Request $request) => Limit::perMinute((int) config('ekotrip.reports.per_minute'))->by($request->ip()));
    }
}
