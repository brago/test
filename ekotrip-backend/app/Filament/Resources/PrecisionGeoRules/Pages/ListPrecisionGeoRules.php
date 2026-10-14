<?php

namespace App\Filament\Resources\PrecisionGeoRules\Pages;

use App\Filament\Resources\PrecisionGeoRules\PrecisionGeoRuleResource;
use Filament\Actions\CreateAction;
use Filament\Resources\Pages\ListRecords;

class ListPrecisionGeoRules extends ListRecords
{
    protected static string $resource = PrecisionGeoRuleResource::class;

    protected function getHeaderActions(): array
    {
        return [CreateAction::make()];
    }
}
