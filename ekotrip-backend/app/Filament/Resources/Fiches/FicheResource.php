<?php

namespace App\Filament\Resources\Fiches;

use App\Filament\Resources\Fiches\Pages\EditFiche;
use App\Filament\Resources\Fiches\Pages\ListFiches;
use App\Models\Fiche;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Forms\Components\Toggle;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\Filter;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Filters\TernaryFilter;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Builder;
use UnitEnum;

/**
 * Vue de contrôle des fiches (Lot 1.1 §10, Lot 1.4 §16).
 * Pas de création ni de suppression : les fiches maîtres viennent du classeur ; on archive.
 */
class FicheResource extends Resource
{
    protected static ?string $model = Fiche::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedMapPin;

    protected static string|UnitEnum|null $navigationGroup = 'Catalogue';

    protected static ?int $navigationSort = 1;

    protected static ?string $modelLabel = 'fiche';

    protected static ?string $recordTitleAttribute = 'nom';

    public const PUBLICATION_STATUSES = [
        'published' => 'Publiée',
        'pending_review' => 'À valider',
        'unpublished' => 'Dépubliée',
        'archived' => 'Archivée',
    ];

    public static function getNavigationBadge(): ?string
    {
        $count = Fiche::query()->toGeocode()->count();

        return $count ? (string) $count : null;
    }

    public static function getNavigationBadgeTooltip(): ?string
    {
        return 'Fiches publiques à géocoder';
    }

    public static function form(Schema $schema): Schema
    {
        $bbox = config('ekotrip.geo.bbox');

        return $schema->components([
            Section::make('Identité')->columns(2)->schema([
                TextEntry::make('id')->label('ID (stable)'),
                TextInput::make('nom')->required()->maxLength(255),
                TextInput::make('commune')->maxLength(100),
                TextInput::make('code_postal')->maxLength(10),
                Textarea::make('adresse')->columnSpanFull(),
                Select::make('categories')
                    ->label('Catégories (principale + associées)')
                    ->relationship('categories', 'name')
                    ->multiple()
                    ->preload()
                    ->helperText('Une entité réelle = une seule fiche ; les rubriques multiples passent par les catégories associées.'),
                TextEntry::make('categorie')->label('Catégorie principale (source)'),
            ]),

            Section::make('Publication')->columns(2)->schema([
                Select::make('publication_status')->label('Statut de publication')
                    ->options(self::PUBLICATION_STATUSES)->required()
                    ->helperText('Seules les fiches actives et « Publiée » sont visibles du public.'),
                Toggle::make('is_active')->label('Active au catalogue'),
                TextEntry::make('publication')->label('Publication (valeur source)'),
            ]),

            Section::make('Carte')->columns(2)->schema([
                Toggle::make('show_on_map')->label('Afficher sur la carte')
                    ->helperText('Désactiver retire immédiatement la fiche de la carte sans la supprimer.'),
                TextEntry::make('map_eligibility')->label('Éligible à la carte')
                    ->state(fn (?Fiche $record) => match (true) {
                        $record === null => null,
                        $record->isOnMap() => 'Oui',
                        $record->needsGeocoding() => 'Non — à géocoder',
                        $record->is_editorial => 'Non — contenu éditorial (jamais sur la carte)',
                        default => 'Non',
                    })
                    ->color(fn (?Fiche $record) => $record?->needsGeocoding() ? 'warning' : null),
                TextInput::make('latitude')->numeric()->minValue($bbox['min_lat'])->maxValue($bbox['max_lat'])
                    ->requiredWith('longitude')
                    ->helperText('Vide = non renseigné. Ne jamais saisir une position approximative.'),
                TextInput::make('longitude')->numeric()->minValue($bbox['min_lng'])->maxValue($bbox['max_lng'])
                    ->requiredWith('latitude'),
                TextInput::make('precision_geo')->label('precision_geo')->maxLength(255)->columnSpanFull(),
                TextEntry::make('geo_display_type')->label('Classe géo (calculée)'),
                TextEntry::make('geo_display_label')->label('Libellé utilisateur (calculé)'),
            ]),

            Section::make('Contenu public')->columns(2)->collapsible()->schema([
                TextInput::make('accroche_courte')->maxLength(255)->columnSpanFull(),
                Textarea::make('description')->rows(6)->columnSpanFull(),
                TextInput::make('telephone')->maxLength(100),
                TextInput::make('site_internet')->url()->maxLength(2048),
                TextInput::make('instagram')->maxLength(2048),
                TextInput::make('facebook')->maxLength(2048),
                Textarea::make('horaires'),
                Textarea::make('tarif_indicatif'),
                Textarea::make('services_equipements'),
                Textarea::make('engagements_eco')->helperText('Uniquement des engagements documentés. Ne crée jamais de badge.'),
                Textarea::make('public_cible'),
                TextInput::make('duree_recommandee')->maxLength(255),
                TextInput::make('budget')->maxLength(50)->helperText('Vide = non renseigné.'),
                Textarea::make('ambiance'),
                TextInput::make('interieur_exterieur')->maxLength(100),
                TextInput::make('niveau_activite')->maxLength(100),
                Textarea::make('accessibilite')->helperText('Vide = inconnu, jamais « non accessible ».'),
                Textarea::make('tags_ia')->label('Mots-clés (recherche / IA)'),
            ]),

            Section::make('Éco ÉKO TRIP')->columns(2)->collapsible()->schema([
                TextEntry::make('eco_badge_public')->label('Badge public')
                    ->placeholder('Aucun badge')
                    ->helperText('Dérivé uniquement d\'une évaluation validée avec preuve (menu Évaluations éco).'),
                TextEntry::make('niveau_ekotrip')->label('Niveau legacy (interne)')->placeholder('—'),
                TextEntry::make('score_ekotrip')->label('Score pré-évalué (interne)')->placeholder('—'),
                TextEntry::make('statut_evaluation')->label('Statut d\'évaluation (interne)')->placeholder('—'),
            ]),

            Section::make('Traçabilité (interne)')->columns(1)->collapsed()->schema([
                TextEntry::make('source_principale')->placeholder('—'),
                TextEntry::make('source_geolocalisation')->placeholder('—'),
                TextEntry::make('raw_placeholders')->label('Placeholders neutralisés à l\'import')->placeholder('—')
                    ->state(fn (?Fiche $record) => $record?->raw_placeholders ? json_encode($record->raw_placeholders, JSON_UNESCAPED_UNICODE) : null),
                TextInput::make('date_derniere_verification')->type('date')
                    ->helperText('À renseigner uniquement après une vérification réelle.'),
                TextInput::make('source_derniere_verification')->maxLength(2048),
                Select::make('statut_verification')->options([
                    'A_VERIFIER' => 'À vérifier', 'VERIFIE' => 'Vérifié', 'A_RECONTROLER' => 'À recontrôler',
                ])->placeholder('Non renseigné'),
            ]),
        ]);
    }

    public static function table(Table $table): Table
    {
        return $table
            ->defaultSort('id')
            ->columns([
                TextColumn::make('id')->label('ID')->searchable()->sortable(),
                TextColumn::make('nom')->searchable()->sortable()->wrap(),
                TextColumn::make('commune')->searchable()->sortable(),
                TextColumn::make('categories.name')->label('Catégories')->badge(),
                TextColumn::make('publication_status')->label('Publication')->badge()
                    ->formatStateUsing(fn ($state) => self::PUBLICATION_STATUSES[$state] ?? $state)
                    ->color(fn ($state) => match ($state) {
                        'published' => 'success', 'pending_review' => 'warning', 'archived' => 'danger', default => 'gray',
                    }),
                IconColumn::make('is_active')->label('Active')->boolean(),
                IconColumn::make('show_on_map')->label('Carte')->boolean(),
                TextColumn::make('a_geocoder')->label('À géocoder')->badge()->color('warning')
                    ->state(fn (Fiche $record) => $record->needsGeocoding() ? 'À géocoder' : null),
                TextColumn::make('geo_display_type')->label('Classe géo')->badge()->toggleable(),
                TextColumn::make('latitude')->toggleable(isToggledHiddenByDefault: true),
                TextColumn::make('longitude')->toggleable(isToggledHiddenByDefault: true),
                TextColumn::make('eco_badge_public')->label('Badge éco')->placeholder('—')->toggleable(),
            ])
            ->filters([
                SelectFilter::make('publication_status')->label('Publication')->options(self::PUBLICATION_STATUSES),
                TernaryFilter::make('is_active')->label('Active'),
                TernaryFilter::make('show_on_map')->label('Sur la carte (source)'),
                Filter::make('public')->label('Visibles du public')->query(fn (Builder $query) => $query->public()),
                Filter::make('a_geocoder')->label('À géocoder')->query(fn (Builder $query) => $query->toGeocode()),
                Filter::make('sans_coordonnees')->label('Sans coordonnées')->query(fn (Builder $query) => $query->whereNull('latitude')),
                SelectFilter::make('geo_display_type')->label('Classe géo')
                    ->options(array_combine(Fiche::GEO_TYPES, Fiche::GEO_TYPES)),
                SelectFilter::make('categories')->label('Catégorie')->relationship('categories', 'name')->multiple(),
            ])
            ->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListFiches::route('/'),
            'edit' => EditFiche::route('/{record}/edit'),
        ];
    }
}
