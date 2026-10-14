<?php

namespace App\Filament\Resources\Fiches\Pages;

use App\Filament\Resources\Fiches\FicheResource;
use Filament\Resources\Pages\ListRecords;

class ListFiches extends ListRecords
{
    protected static string $resource = FicheResource::class;
}
