<?php

namespace App\Filament\Resources\Categories;

use App\Filament\Resources\Categories\Pages\EditCategory;
use App\Filament\Resources\Categories\Pages\ListCategories;
use App\Models\Category;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\TextInput;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use UnitEnum;

class CategoryResource extends Resource
{
    protected static ?string $model = Category::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedTag;

    protected static string|UnitEnum|null $navigationGroup = 'Catalogue';

    protected static ?int $navigationSort = 2;

    protected static ?string $modelLabel = 'catégorie';

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            TextEntry::make('name')->label('Catégorie (source CATEGORIES)'),
            TextInput::make('sort_order')->label('Ordre')->numeric()->required(),
            Select::make('map_group_id')->label('Groupe carte')->relationship('mapGroup', 'label')
                ->helperText('Correspondance catégorie → groupe cartographique, modifiable sans code (Lot 1.4 §4).'),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('sort_order')->columns([
            TextColumn::make('sort_order')->label('Ordre'),
            TextColumn::make('name')->label('Catégorie'),
            TextColumn::make('mapGroup.label')->label('Groupe carte')->badge()->placeholder('Aucun'),
            TextColumn::make('fiches_count')->label('Fiches')->counts('fiches'),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return ['index' => ListCategories::route('/'), 'edit' => EditCategory::route('/{record}/edit')];
    }
}
