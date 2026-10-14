<?php

namespace App\Filament\Resources\Circuits\RelationManagers;

use Filament\Resources\RelationManagers\RelationManager;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class NotesRelationManager extends RelationManager
{
    protected static string $relationship = 'notes';

    protected static ?string $title = 'Précautions et hébergements';

    public function isReadOnly(): bool
    {
        return true;
    }

    public function table(Table $table): Table
    {
        return $table->paginated(false)->columns([
            TextColumn::make('kind')->label('Type')->badge(),
            TextColumn::make('text')->label('Texte')->wrap(),
            TextColumn::make('fiche_id')->label('Fiche'),
        ]);
    }
}
