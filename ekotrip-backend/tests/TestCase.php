<?php

namespace Tests;

use Illuminate\Foundation\Testing\TestCase as BaseTestCase;
use Illuminate\Support\Facades\Cache;

abstract class TestCase extends BaseTestCase
{
    protected function setUp(): void
    {
        parent::setUp();

        // Le cache (store array) vit le temps du processus : on repart d'un cache vide à chaque test.
        Cache::flush();
    }

    protected function setUpTraits()
    {
        // Import avant l'ouverture de la transaction de test (DatabaseTransactions).
        if (in_array(Concerns\WithImportedCatalogue::class, class_uses_recursive(static::class), true)) {
            $this->importCatalogueIfNeeded();
        }

        return parent::setUpTraits();
    }
}
