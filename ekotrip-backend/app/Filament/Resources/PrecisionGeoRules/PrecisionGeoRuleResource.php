<?php

namespace App\Filament\Resources\PrecisionGeoRules;

use App\Filament\Resources\PrecisionGeoRules\Pages\CreatePrecisionGeoRule;
use App\Filament\Resources\PrecisionGeoRules\Pages\EditPrecisionGeoRule;
use App\Filament\Resources\PrecisionGeoRules\Pages\ListPrecisionGeoRules;
use App\Models\Fiche;
use App\Models\PrecisionGeoRule;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Hidden;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\TextInput;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use UnitEnum;

/**
 * precision_geo → classe d'affichage (63_CARTO_ROUTAGE_V1).
 * Une règle modifiée ici devient ADMIN (jamais écrasée par l'import) et s'applique aux fiches concernées.
 */
class PrecisionGeoRuleResource extends Resource
{
    protected static ?string $model = PrecisionGeoRule::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedViewfinderCircle;

    protected static string|UnitEnum|null $navigationGroup = 'Carte';

    protected static ?string $modelLabel = 'règle de précision GPS';

    protected static ?string $pluralModelLabel = 'règles de précision GPS';

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            TextInput::make('precision_geo')->label('Libellé precision_geo')->required()->maxLength(255)
                ->unique(ignoreRecord: true)->columnSpanFull(),
            Select::make('geo_display_type')->label('Classe')->required()
                ->options(collect(config('ekotrip.geo.labels'))->mapWithKeys(fn ($l, $t) => [$t => "{$t} — {$l}"])),
            Hidden::make('origin')->default('ADMIN')->dehydrateStateUsing(fn () => 'ADMIN'),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('precision_geo')->columns([
            TextColumn::make('precision_geo')->label('precision_geo')->searchable()->wrap(),
            TextColumn::make('geo_display_type')->label('Classe')->badge(),
            TextColumn::make('origin')->label('Origine')->badge(),
            TextColumn::make('fiches')->label('Fiches')
                ->state(fn (PrecisionGeoRule $r) => Fiche::where('precision_geo', $r->precision_geo)->count()),
        ])->filters([
            SelectFilter::make('geo_display_type')->label('Classe')->options(array_combine(Fiche::GEO_TYPES, Fiche::GEO_TYPES)),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListPrecisionGeoRules::route('/'),
            'create' => CreatePrecisionGeoRule::route('/create'),
            'edit' => EditPrecisionGeoRule::route('/{record}/edit'),
        ];
    }
}
