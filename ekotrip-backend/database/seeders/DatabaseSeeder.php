<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Artisan;

class DatabaseSeeder extends Seeder
{
    /**
     * Les données réelles viennent exclusivement du classeur canonique :
     * aucune fiche fictive n'est créée. Admin : php artisan ekotrip:create-admin <email>.
     */
    public function run(): void
    {
        Artisan::call('ekotrip:import', [], $this->command?->getOutput());
    }
}
