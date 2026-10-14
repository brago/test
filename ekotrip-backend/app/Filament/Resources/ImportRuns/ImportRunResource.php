<?php

namespace App\Filament\Resources\ImportRuns;

use App\Filament\Resources\ImportRuns\Pages\ListImportRuns;
use App\Filament\Resources\ImportRuns\Pages\ViewImportRun;
use App\Filament\Resources\ImportRuns\RelationManagers\IssuesRelationManager;
use App\Models\ImportRun;
use BackedEnum;
use Filament\Actions\ViewAction;
use Filament\Infolists\Components\KeyValueEntry;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use UnitEnum;

/** Rapports d'import (Lot 1.1 §8) — consultation seule. */
class ImportRunResource extends Resource
{
    protected static ?string $model = ImportRun::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedArrowDownTray;

    protected static string|UnitEnum|null $navigationGroup = 'Qualité';

    protected static ?string $modelLabel = 'rapport d\'import';

    protected static ?string $pluralModelLabel = 'rapports d\'import';

    public static function infolist(Schema $schema): Schema
    {
        return $schema->components([
            Section::make('Import')->columns(4)->schema([
                TextEntry::make('id')->label('#'),
                TextEntry::make('status')->label('Statut')->badge(),
                TextEntry::make('dry_run')->label('Simulation')->formatStateUsing(fn ($s) => $s ? 'Oui' : 'Non'),
                TextEntry::make('source'),
                TextEntry::make('started_at')->label('Début')->dateTime(),
                TextEntry::make('finished_at')->label('Fin')->dateTime(),
                TextEntry::make('source_sha1')->label('Empreinte du classeur')->columnSpan(2),
            ]),
            Section::make('Synthèse')->columns(3)->schema([
                KeyValueEntry::make('summary.counters')->label('Compteurs'),
                KeyValueEntry::make('summary.issues_by_severity')->label('Anomalies par sévérité'),
                KeyValueEntry::make('summary.issues_by_code')->label('Anomalies par code'),
            ]),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('id', 'desc')->columns([
            TextColumn::make('id')->label('#'),
            TextColumn::make('started_at')->label('Date')->dateTime(),
            TextColumn::make('status')->label('Statut')->badge()->color(fn ($s) => $s === 'SUCCESS' ? 'success' : 'danger'),
            IconColumn::make('dry_run')->label('Simulation')->boolean(),
            TextColumn::make('summary.counters.fiches_read')->label('Lues'),
            TextColumn::make('summary.counters.fiches_public')->label('Publiques'),
            TextColumn::make('summary.counters.fiches_on_map')->label('Carte'),
            TextColumn::make('summary.issues_by_severity.ERROR')->label('Erreurs')->default(0),
            TextColumn::make('summary.issues_by_severity.WARNING')->label('Alertes')->default(0),
        ])->recordActions([ViewAction::make()]);
    }

    public static function getRelations(): array
    {
        return [IssuesRelationManager::class];
    }

    public static function getPages(): array
    {
        return ['index' => ListImportRuns::route('/'), 'view' => ViewImportRun::route('/{record}')];
    }
}
