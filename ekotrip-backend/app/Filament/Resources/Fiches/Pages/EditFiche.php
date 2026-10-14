<?php

namespace App\Filament\Resources\Fiches\Pages;

use App\Filament\Resources\Fiches\FicheResource;
use Filament\Resources\Pages\EditRecord;

class EditFiche extends EditRecord
{
    protected static string $resource = FicheResource::class;

    /** Champs vides du formulaire = non renseigné (NULL), jamais une chaîne vide. */
    protected function mutateFormDataBeforeSave(array $data): array
    {
        return array_map(fn ($v) => is_string($v) && trim($v) === '' ? null : $v, $data);
    }

    protected function afterSave(): void
    {
        $this->record->refreshSearchCategories();
    }
}
