<?php

namespace App\Filament\Resources\Circuits\RelationManagers;

use App\Models\CircuitStep;
use Filament\Resources\RelationManagers\RelationManager;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class StepsRelationManager extends RelationManager
{
    protected static string $relationship = 'steps';

    protected static ?string $title = 'Étapes';

    public function isReadOnly(): bool
    {
        return true;
    }

    public function table(Table $table): Table
    {
        return $table->defaultSort('day')->paginated(false)->columns([
            TextColumn::make('day')->label('Jour'),
            TextColumn::make('position')->label('#'),
            TextColumn::make('label')->label('Étape')->wrap(),
            TextColumn::make('fiche_id')->label('Fiche maître'),
            TextColumn::make('decision')->label('Décision')->badge(),
            IconColumn::make('servie')->label('Servie')->boolean()
                ->state(fn (CircuitStep $s) => $s->is_retained && $s->fiche?->isPublic()),
        ]);
    }
}
