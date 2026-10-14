<?php

namespace App\Filament\Resources\Communes;

use App\Filament\Resources\Communes\Pages\ListCommunes;
use App\Models\Commune;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use UnitEnum;

/** Référentiel officiel des 34 communes (COG INSEE, codes postaux La Poste) — consultation seule. */
class CommuneResource extends Resource
{
    protected static ?string $model = Commune::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedMapPin;

    protected static string|UnitEnum|null $navigationGroup = 'Carte';

    protected static ?string $modelLabel = 'commune officielle';

    protected static ?string $pluralModelLabel = 'communes officielles';

    public static function table(Table $table): Table
    {
        return $table->defaultSort('nom')->paginated(false)->columns([
            TextColumn::make('code_insee')->label('Code INSEE')->searchable(),
            TextColumn::make('nom')->searchable(),
            TextColumn::make('codes_postaux')->label('Codes postaux')->badge(),
            TextColumn::make('mappings_count')->label('Valeurs brutes rattachées')->counts('mappings'),
        ]);
    }

    public static function getPages(): array
    {
        return ['index' => ListCommunes::route('/')];
    }
}
