<?php

namespace App\Filament\Resources\EcoEvaluations\Pages;

use App\Filament\Resources\EcoEvaluations\EcoEvaluationResource;
use App\Services\Catalogue\EcoBadgeResolver;
use Filament\Resources\Pages\CreateRecord;

class CreateEcoEvaluation extends CreateRecord
{
    protected static string $resource = EcoEvaluationResource::class;

    protected function mutateFormDataBeforeCreate(array $data): array
    {
        return EcoEvaluationResource::stamp($data);
    }

    /** Le badge dépend aussi des preuves, enregistrées après l'évaluation. */
    protected function afterCreate(): void
    {
        app(EcoBadgeResolver::class)->refresh($this->record->fiche);
    }
}
