<?php

namespace App\Filament\Resources\Circuits;

use App\Filament\Resources\Circuits\Pages\ListCircuits;
use App\Filament\Resources\Circuits\Pages\ViewCircuit;
use App\Filament\Resources\Circuits\RelationManagers\NotesRelationManager;
use App\Filament\Resources\Circuits\RelationManagers\StepsRelationManager;
use App\Models\Circuit;
use BackedEnum;
use Filament\Actions\ViewAction;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use UnitEnum;

/** Circuits issus de l'import (05A / 05 / 05D) — contrôle des étapes et de leurs fiches maîtres. */
class CircuitResource extends Resource
{
    protected static ?string $model = Circuit::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedMap;

    protected static string|UnitEnum|null $navigationGroup = 'Catalogue';

    protected static ?int $navigationSort = 5;

    public static function infolist(Schema $schema): Schema
    {
        return $schema->columns(3)->components([
            TextEntry::make('id'),
            TextEntry::make('nom')->columnSpan(2),
            TextEntry::make('resume')->columnSpanFull(),
            TextEntry::make('duree_totale'),
            TextEntry::make('difficulte'),
            TextEntry::make('mode_deplacement'),
            TextEntry::make('distance_totale')->placeholder('Non validée'),
            TextEntry::make('point_depart'),
            TextEntry::make('statut'),
            TextEntry::make('notes')->columnSpanFull(),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('sort_order')->columns([
            TextColumn::make('id'),
            TextColumn::make('nom')->wrap(),
            TextColumn::make('nb_jours')->label('Jours'),
            TextColumn::make('steps_count')->label('Étapes')->counts('steps'),
            TextColumn::make('statut')->badge(),
        ])->recordActions([ViewAction::make()]);
    }

    public static function getRelations(): array
    {
        return [StepsRelationManager::class, NotesRelationManager::class];
    }

    public static function getPages(): array
    {
        return ['index' => ListCircuits::route('/'), 'view' => ViewCircuit::route('/{record}')];
    }
}
