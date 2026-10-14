<?php

namespace App\Filament\Resources\CommuneMappings;

use App\Filament\Resources\CommuneMappings\Pages\CreateCommuneMapping;
use App\Filament\Resources\CommuneMappings\Pages\EditCommuneMapping;
use App\Filament\Resources\CommuneMappings\Pages\ListCommuneMappings;
use App\Models\CommuneMapping;
use App\Models\Fiche;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Hidden;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use UnitEnum;

/**
 * Correspondance des communes (décision 9) : valeur brute → communes officielles + lieu-dit + zone.
 * Modifier une ligne ici la rend « ADMIN » : l'import ne l'écrasera plus.
 */
class CommuneMappingResource extends Resource
{
    protected static ?string $model = CommuneMapping::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedBuildingLibrary;

    protected static string|UnitEnum|null $navigationGroup = 'Carte';

    protected static ?string $modelLabel = 'correspondance de commune';

    protected static ?string $pluralModelLabel = 'correspondance des communes';

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            TextInput::make('raw_value')->label('Valeur brute (source)')->required()->maxLength(255)
                ->unique(ignoreRecord: true)->disabledOn('edit')->columnSpanFull(),
            Select::make('communes')->label('Communes officielles')->relationship('communes', 'nom')
                ->multiple()->preload()->helperText('Vide pour une zone seule (ex. « Nord Caraïbe »).'),
            Select::make('type')->required()->options(array_combine(CommuneMapping::TYPES, CommuneMapping::TYPES)),
            TextInput::make('lieu_dit')->label('Lieu-dit')->maxLength(255),
            TextInput::make('zone')->maxLength(100),
            Select::make('statut_validation')->label('Validation')->required()
                ->options(['VALIDE' => 'Validée', 'PROVISOIRE' => 'Provisoire']),
            Textarea::make('decision')->label('Décision')->columnSpanFull(),
            Hidden::make('origin')->default('ADMIN')->dehydrateStateUsing(fn () => 'ADMIN'),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('raw_value')->columns([
            TextColumn::make('raw_value')->label('Valeur brute')->searchable()->wrap(),
            TextColumn::make('communes.nom')->label('Communes officielles')->badge()->placeholder('—'),
            TextColumn::make('lieu_dit')->label('Lieu-dit')->placeholder('—'),
            TextColumn::make('zone')->placeholder('—'),
            TextColumn::make('type')->badge(),
            TextColumn::make('statut_validation')->label('Validation')->badge()
                ->color(fn ($s) => $s === 'PROVISOIRE' ? 'warning' : 'success'),
            TextColumn::make('fiches')->label('Fiches')
                ->state(fn (CommuneMapping $m) => Fiche::where('commune', $m->raw_value)->count()),
            TextColumn::make('origin')->label('Origine')->badge()->toggleable(),
        ])->filters([
            SelectFilter::make('statut_validation')->label('Validation')->options(['VALIDE' => 'Validée', 'PROVISOIRE' => 'Provisoire']),
            SelectFilter::make('type')->options(array_combine(CommuneMapping::TYPES, CommuneMapping::TYPES)),
            SelectFilter::make('communes')->label('Commune')->relationship('communes', 'nom'),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListCommuneMappings::route('/'),
            'create' => CreateCommuneMapping::route('/create'),
            'edit' => EditCommuneMapping::route('/{record}/edit'),
        ];
    }
}
