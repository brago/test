<?php

namespace App\Filament\Resources\MapGroups;

use App\Filament\Resources\MapGroups\Pages\CreateMapGroup;
use App\Filament\Resources\MapGroups\Pages\EditMapGroup;
use App\Filament\Resources\MapGroups\Pages\ListMapGroups;
use App\Models\MapGroup;
use BackedEnum;
use Filament\Actions\DeleteAction;
use Filament\Actions\EditAction;
use Filament\Forms\Components\ColorPicker;
use Filament\Forms\Components\TextInput;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\ColorColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use UnitEnum;

class MapGroupResource extends Resource
{
    protected static ?string $model = MapGroup::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedSquares2x2;

    protected static string|UnitEnum|null $navigationGroup = 'Carte';

    protected static ?string $modelLabel = 'groupe carte';

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            TextInput::make('label')->label('Libellé')->required()->maxLength(100),
            TextInput::make('slug')->required()->maxLength(50)->alphaDash()->unique(ignoreRecord: true),
            TextInput::make('icon')->label('Icône')->maxLength(50)
                ->helperText('La couleur ne doit jamais porter seule l\'information : une icône est requise.'),
            ColorPicker::make('color')->label('Couleur'),
            TextInput::make('sort_order')->label('Ordre')->numeric()->default(0),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('sort_order')->columns([
            TextColumn::make('sort_order')->label('Ordre'),
            TextColumn::make('label')->label('Groupe'),
            TextColumn::make('slug'),
            TextColumn::make('icon')->label('Icône'),
            ColorColumn::make('color')->label('Couleur'),
            TextColumn::make('categories.name')->label('Catégories')->badge(),
        ])->recordActions([EditAction::make(), DeleteAction::make()]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListMapGroups::route('/'),
            'create' => CreateMapGroup::route('/create'),
            'edit' => EditMapGroup::route('/{record}/edit'),
        ];
    }
}
