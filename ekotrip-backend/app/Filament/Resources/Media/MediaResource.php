<?php

namespace App\Filament\Resources\Media;

use App\Filament\Resources\Media\Pages\CreateMedia;
use App\Filament\Resources\Media\Pages\EditMedia;
use App\Filament\Resources\Media\Pages\ListMedia;
use App\Models\Media;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\DatePicker;
use Filament\Forms\Components\DateTimePicker;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Forms\Components\Toggle;
use Filament\Resources\Resource;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Components\Utilities\Get;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use UnitEnum;

/**
 * Médias multi-source (06_MEDIAS, 78_MEDIAS_SOURCES_V11).
 * Seul ACTIVE + droits qualifiés est servi ; TO_VERIFY / BLOCKED jamais ; Google Places reste dynamique.
 */
class MediaResource extends Resource
{
    protected static ?string $model = Media::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedPhoto;

    protected static string|UnitEnum|null $navigationGroup = 'Catalogue';

    protected static ?int $navigationSort = 4;

    protected static ?string $modelLabel = 'média';

    private static function options(array $values): array
    {
        return array_combine($values, $values);
    }

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            Section::make('Cible')->columns(3)->schema([
                TextInput::make('id')->label('media_id')->required()->maxLength(40)->unique(ignoreRecord: true)
                    ->disabledOn('edit'),
                Select::make('target_type')->required()->options(self::options(Media::TARGET_TYPES))->default('FICHE'),
                TextInput::make('target_id')->required()->maxLength(40)
                    ->helperText('ID de la fiche (ou du circuit).')
                    ->rules(fn (Get $get) => $get('target_type') === 'CIRCUIT' ? ['exists:circuits,id'] : ['exists:fiches,id']),
                Select::make('media_role')->required()->options(self::options(Media::ROLES)),
                Toggle::make('is_primary')->label('Photo principale'),
                TextInput::make('sort_order')->numeric(),
            ]),
            Section::make('Source et droits')->columns(2)->schema([
                Select::make('source_type')->required()->live()->options(self::options(Media::SOURCE_TYPES)),
                Select::make('storage_mode')->required()
                    ->options(fn (Get $get) => $get('source_type') === 'GOOGLE_PLACES'
                        ? ['DYNAMIC_PROVIDER' => 'DYNAMIC_PROVIDER']
                        : self::options(Media::STORAGE_MODES)),
                TextInput::make('asset_url_or_ref')->label('URL ou référence')->required()->maxLength(2048)->columnSpanFull(),
                TextInput::make('provider')->maxLength(100),
                TextInput::make('external_asset_id')->maxLength(255),
                TextInput::make('source_page_url')->url()->maxLength(2048)
                    ->required(fn (Get $get) => $get('source_type') === 'OPEN_LICENSE'),
                Select::make('rights_status')->required()->options(self::options(Media::RIGHTS_STATUSES)),
                TextInput::make('license_or_permission')->maxLength(255),
                TextInput::make('license_url')->url()->maxLength(2048),
                TextInput::make('author_or_rights_holder')->maxLength(255),
                TextInput::make('attribution_text')->maxLength(255),
                Toggle::make('attribution_required'),
                Toggle::make('commercial_use_allowed')->helperText('FALSE = non publiable (hors conditions fournisseur Google Places).'),
                TextInput::make('proof_reference')->maxLength(255)
                    ->required(fn (Get $get) => $get('source_type') === 'PARTNER_UPLOAD'),
                DatePicker::make('permission_date'),
                DatePicker::make('expires_at'),
            ]),
            Section::make('Publication')->columns(2)->schema([
                Select::make('publication_status')->required()->default('DRAFT')->options(self::options(Media::PUBLICATION_STATUSES)),
                DateTimePicker::make('last_checked_at')->label('Dernier contrôle réel'),
                TextInput::make('alt_text')->label('Texte alternatif')->maxLength(255)->columnSpanFull(),
                Textarea::make('notes')->columnSpanFull(),
            ]),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('updated_at', 'desc')->columns([
            TextColumn::make('id')->searchable(),
            TextColumn::make('target_type')->badge(),
            TextColumn::make('target_id')->searchable(),
            TextColumn::make('media_role'),
            TextColumn::make('source_type')->badge(),
            TextColumn::make('rights_status')->badge()
                ->color(fn ($s) => in_array($s, ['TO_VERIFY', 'BLOCKED'], true) ? 'danger' : 'success'),
            TextColumn::make('publication_status')->badge()->color(fn ($s) => $s === 'ACTIVE' ? 'success' : 'gray'),
            IconColumn::make('is_primary')->boolean(),
        ])->filters([
            SelectFilter::make('target_type')->options(self::options(Media::TARGET_TYPES)),
            SelectFilter::make('source_type')->options(self::options(Media::SOURCE_TYPES)),
            SelectFilter::make('rights_status')->options(self::options(Media::RIGHTS_STATUSES)),
            SelectFilter::make('publication_status')->options(self::options(Media::PUBLICATION_STATUSES)),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListMedia::route('/'),
            'create' => CreateMedia::route('/create'),
            'edit' => EditMedia::route('/{record}/edit'),
        ];
    }
}
