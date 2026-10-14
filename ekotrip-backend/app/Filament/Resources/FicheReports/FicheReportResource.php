<?php

namespace App\Filament\Resources\FicheReports;

use App\Filament\Resources\FicheReports\Pages\EditFicheReport;
use App\Filament\Resources\FicheReports\Pages\ListFicheReports;
use App\Models\FicheReport;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use UnitEnum;

/** File des signalements (62_EXTENSIONS_SCHEMA) : un signalement ne modifie jamais la fiche. */
class FicheReportResource extends Resource
{
    protected static ?string $model = FicheReport::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedFlag;

    protected static string|UnitEnum|null $navigationGroup = 'Qualité';

    protected static ?string $modelLabel = 'signalement';

    public const STATUSES = ['NOUVEAU' => 'Nouveau', 'EN_COURS' => 'En cours', 'TRAITE' => 'Traité', 'REJETE' => 'Rejeté'];

    public static function getNavigationBadge(): ?string
    {
        $count = FicheReport::where('statut', 'NOUVEAU')->count();

        return $count ? (string) $count : null;
    }

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            TextEntry::make('fiche_id')->label('Fiche'),
            TextEntry::make('fiche.nom')->label('Nom'),
            TextEntry::make('motif'),
            TextEntry::make('created_at')->label('Reçu le')->dateTime(),
            TextEntry::make('commentaire')->placeholder('—')->columnSpanFull(),
            Select::make('statut')->options(self::STATUSES)->required(),
            Textarea::make('decision')->label('Décision / correction effectuée')
                ->helperText('Historique conservé. Corriger la fiche elle-même depuis le menu Fiches.'),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('created_at', 'desc')->columns([
            TextColumn::make('created_at')->label('Reçu le')->dateTime()->sortable(),
            TextColumn::make('fiche_id')->label('Fiche')->searchable(),
            TextColumn::make('fiche.nom')->label('Nom')->wrap(),
            TextColumn::make('motif')->badge(),
            TextColumn::make('commentaire')->limit(60),
            TextColumn::make('statut')->badge()->formatStateUsing(fn ($s) => self::STATUSES[$s] ?? $s)
                ->color(fn ($s) => match ($s) {
                    'NOUVEAU' => 'warning', 'EN_COURS' => 'info', 'TRAITE' => 'success', default => 'gray'
                }),
        ])->filters([
            SelectFilter::make('statut')->options(self::STATUSES),
            SelectFilter::make('motif')->options(array_combine(config('ekotrip.reports.motifs'), config('ekotrip.reports.motifs'))),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return ['index' => ListFicheReports::route('/'), 'edit' => EditFicheReport::route('/{record}/edit')];
    }
}
