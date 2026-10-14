<?php

namespace App\Filament\Resources\ImportRuns\RelationManagers;

use Filament\Resources\RelationManagers\RelationManager;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;

class IssuesRelationManager extends RelationManager
{
    protected static string $relationship = 'issues';

    protected static ?string $title = 'Anomalies';

    public function isReadOnly(): bool
    {
        return true;
    }

    public function table(Table $table): Table
    {
        return $table->defaultSort('id')->columns([
            TextColumn::make('severity')->label('Sévérité')->badge()
                ->color(fn ($s) => match ($s) {
                    'ERROR' => 'danger', 'WARNING' => 'warning', default => 'gray'
                }),
            TextColumn::make('code')->badge(),
            TextColumn::make('sheet')->label('Onglet'),
            TextColumn::make('row_ref')->label('Réf.')->searchable(),
            TextColumn::make('message')->wrap()->searchable(),
        ])->filters([
            SelectFilter::make('severity')->label('Sévérité')->options(['ERROR' => 'Erreur', 'WARNING' => 'Alerte', 'INFO' => 'Info']),
            SelectFilter::make('code')->options(fn () => $this->getOwnerRecord()->issues()->distinct()->pluck('code', 'code')->all()),
        ]);
    }
}
