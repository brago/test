<?php

namespace App\Filament\Resources\EcoEvaluations\Pages;

use App\Filament\Resources\EcoEvaluations\EcoEvaluationResource;
use Filament\Actions\CreateAction;
use Filament\Resources\Pages\ListRecords;

class ListEcoEvaluations extends ListRecords
{
    protected static string $resource = EcoEvaluationResource::class;

    protected function getHeaderActions(): array
    {
        return [CreateAction::make()];
    }
}
