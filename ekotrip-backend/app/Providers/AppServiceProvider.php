<?php

namespace App\Providers;

use App\Models\Category;
use App\Models\Circuit;
use App\Models\EcoEvaluation;
use App\Models\Fiche;
use App\Models\FicheReport;
use App\Models\ImportRun;
use App\Models\MapGroup;
use App\Models\Media;
use App\Models\PrecisionGeoRule;
use App\Models\Setting;
use App\Policies\CategoryPolicy;
use App\Policies\FichePolicy;
use App\Policies\FicheReportPolicy;
use App\Policies\GenericAdminPolicy;
use App\Policies\ReadOnlyAdminPolicy;
use App\Services\Search\MysqlSearchEngine;
use App\Services\Search\SearchEngine;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;
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
        Gate::policy(Fiche::class, FichePolicy::class);
        Gate::policy(Category::class, CategoryPolicy::class);
        Gate::policy(FicheReport::class, FicheReportPolicy::class);
        foreach ([ImportRun::class, Circuit::class] as $model) {
            Gate::policy($model, ReadOnlyAdminPolicy::class);
        }
        foreach ([MapGroup::class, PrecisionGeoRule::class, Media::class, EcoEvaluation::class, Setting::class] as $model) {
            Gate::policy($model, GenericAdminPolicy::class);
        }

        RateLimiter::for('api', fn (Request $request) => Limit::perMinute(120)->by($request->ip()));

        // Anti-spam des signalements (62_EXTENSIONS_SCHEMA : « Limiter et journaliser »).
        RateLimiter::for('fiche-reports', fn (Request $request) => Limit::perMinute((int) config('ekotrip.reports.per_minute'))->by($request->ip()));
    }
}
