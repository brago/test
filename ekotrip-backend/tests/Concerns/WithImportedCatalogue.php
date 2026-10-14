<?php

namespace Tests\Concerns;

use App\Models\Fiche;
use App\Services\Import\CatalogueImporter;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schema;

trait WithImportedCatalogue
{
    private static bool $catalogueImported = false;

    protected function importCatalogueIfNeeded(): void
    {
        if (self::$catalogueImported && Schema::hasTable('fiches') && Fiche::query()->count() === 458) {
            return;
        }

        Artisan::call('migrate:fresh', ['--force' => true]);
        $run = app(CatalogueImporter::class)->run();

        if ($run->status !== 'SUCCESS') {
            throw new \RuntimeException('Import du catalogue de test en échec : '.json_encode($run->summary));
        }

        self::$catalogueImported = true;
    }
}
