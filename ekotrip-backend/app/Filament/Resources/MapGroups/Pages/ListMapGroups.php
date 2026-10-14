<?php

namespace App\Filament\Resources\MapGroups\Pages;

use App\Filament\Resources\MapGroups\MapGroupResource;
use Filament\Actions\CreateAction;
use Filament\Resources\Pages\ListRecords;

class ListMapGroups extends ListRecords
{
    protected static string $resource = MapGroupResource::class;

    protected function getHeaderActions(): array
    {
        return [CreateAction::make()];
    }
}
