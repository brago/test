<?php

namespace App\Filament\Resources\Settings;

use App\Filament\Resources\Settings\Pages\EditSetting;
use App\Filament\Resources\Settings\Pages\ListSettings;
use App\Models\Setting;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\TextInput;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use UnitEnum;

/** Paramètres administrables (jamais codés en dur). */
class SettingResource extends Resource
{
    protected static ?string $model = Setting::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedCog6Tooth;

    protected static string|UnitEnum|null $navigationGroup = 'Configuration';

    protected static ?string $modelLabel = 'paramètre';

    public const LABELS = [
        'nearby_radius_km' => 'Rayon « Autour de moi » par défaut (km)',
    ];

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            TextEntry::make('key')->label('Paramètre')->formatStateUsing(fn ($k) => self::LABELS[$k] ?? $k),
            TextInput::make('value')->label('Valeur')->numeric()->minValue(0.1)->maxValue(100)->required(),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->columns([
            TextColumn::make('key')->label('Paramètre')->formatStateUsing(fn ($k) => self::LABELS[$k] ?? $k),
            TextColumn::make('value')->label('Valeur'),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return ['index' => ListSettings::route('/'), 'edit' => EditSetting::route('/{record}/edit')];
    }
}
