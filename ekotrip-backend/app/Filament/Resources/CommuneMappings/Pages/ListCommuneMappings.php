<?php

namespace App\Filament\Resources\CommuneMappings\Pages;

use App\Filament\Resources\CommuneMappings\CommuneMappingResource;
use Filament\Actions\CreateAction;
use Filament\Resources\Pages\ListRecords;

class ListCommuneMappings extends ListRecords
{
    protected static string $resource = CommuneMappingResource::class;

    protected function getHeaderActions(): array
    {
        return [CreateAction::make()];
    }
}
