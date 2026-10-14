<?php

namespace App\Filament\Resources\EcoEvaluations\Pages;

use App\Filament\Resources\EcoEvaluations\EcoEvaluationResource;
use App\Services\Catalogue\EcoBadgeResolver;
use Filament\Resources\Pages\EditRecord;

class EditEcoEvaluation extends EditRecord
{
    protected static string $resource = EcoEvaluationResource::class;

    protected function mutateFormDataBeforeSave(array $data): array
    {
        return EcoEvaluationResource::stamp($data);
    }

    /** Le badge dépend aussi des preuves, enregistrées après l'évaluation. */
    protected function afterEdit(): void
    {
        app(EcoBadgeResolver::class)->refresh($this->record->fiche);
    }
}
