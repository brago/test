<?php

namespace App\Filament\Resources\EcoEvaluations;

use App\Filament\Resources\EcoEvaluations\Pages\CreateEcoEvaluation;
use App\Filament\Resources\EcoEvaluations\Pages\EditEcoEvaluation;
use App\Filament\Resources\EcoEvaluations\Pages\ListEcoEvaluations;
use App\Models\EcoEvaluation;
use App\Models\EcoProof;
use App\Services\Catalogue\EcoBadgeResolver;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\DatePicker;
use Filament\Forms\Components\Repeater;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Resources\Resource;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use UnitEnum;

/**
 * Évaluations éco + preuves (Lot 1.1 §5, 07_REF_SCORE). Seule une évaluation VALIDATED
 * avec au moins une preuve VALIDATED produit un badge public ; « Vérifié » ≠ écoresponsable.
 */
class EcoEvaluationResource extends Resource
{
    protected static ?string $model = EcoEvaluation::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedSparkles;

    protected static string|UnitEnum|null $navigationGroup = 'Catalogue';

    protected static ?int $navigationSort = 3;

    protected static ?string $modelLabel = 'évaluation éco';

    protected static ?string $pluralModelLabel = 'évaluations éco';

    public static function form(Schema $schema): Schema
    {
        return $schema->components([
            Section::make('Évaluation')->columns(2)->schema([
                Select::make('fiche_id')->label('Fiche')->relationship('fiche', 'nom')
                    ->getOptionLabelFromRecordUsing(fn ($record) => "{$record->id} — {$record->nom}")
                    ->searchable(['id', 'nom'])->required(),
                TextInput::make('score')->numeric()->minValue(0)->maxValue(100)->required()
                    ->helperText('Niveau public dérivé du référentiel 07_REF_SCORE.'),
                Select::make('status')->label('Statut')->required()->default('DRAFT')->options([
                    'DRAFT' => 'Brouillon', 'VALIDATED' => 'Validée', 'REVOKED' => 'Révoquée',
                ]),
                Textarea::make('criteria')->label('Critères documentés')->columnSpanFull(),
                Textarea::make('notes')->columnSpanFull(),
            ]),
            Section::make('Preuves')->schema([
                Repeater::make('proofs')->relationship()->columns(2)->defaultItems(0)->schema([
                    Select::make('kind')->label('Type')->required()->options(array_combine(EcoProof::KINDS, EcoProof::KINDS)),
                    TextInput::make('reference')->label('Référence / URL')->required()->maxLength(255),
                    DatePicker::make('document_date')->label('Date du document'),
                    Select::make('status')->label('Statut')->required()->default('PENDING')
                        ->options(['PENDING' => 'À examiner', 'VALIDATED' => 'Validée', 'REJECTED' => 'Rejetée', 'REVOKED' => 'Révoquée']),
                    Textarea::make('notes')->columnSpanFull(),
                ]),
            ]),
        ]);
    }

    /** Traçabilité : qui a validé, quand ; révocation horodatée. */
    public static function stamp(array $data): array
    {
        if (($data['status'] ?? null) === 'VALIDATED') {
            $data['validated_by'] ??= auth()->id();
            $data['validated_at'] ??= now();
            $data['revoked_at'] = null;
        }
        if (($data['status'] ?? null) === 'REVOKED') {
            $data['revoked_at'] ??= now();
        }

        return $data;
    }

    public static function table(Table $table): Table
    {
        return $table->defaultSort('updated_at', 'desc')->columns([
            TextColumn::make('fiche_id')->label('Fiche')->searchable(),
            TextColumn::make('fiche.nom')->label('Nom')->wrap(),
            TextColumn::make('score'),
            TextColumn::make('niveau')->label('Niveau dérivé')->state(fn (EcoEvaluation $e) => EcoBadgeResolver::levelFor($e->score))->placeholder('—'),
            TextColumn::make('status')->label('Statut')->badge(),
            TextColumn::make('proofs_count')->label('Preuves')->counts('proofs'),
            TextColumn::make('fiche.eco_badge_public')->label('Badge public')->placeholder('Aucun'),
        ])->filters([
            SelectFilter::make('status')->options(['DRAFT' => 'Brouillon', 'VALIDATED' => 'Validée', 'REVOKED' => 'Révoquée']),
        ])->recordActions([EditAction::make()]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListEcoEvaluations::route('/'),
            'create' => CreateEcoEvaluation::route('/create'),
            'edit' => EditEcoEvaluation::route('/{record}/edit'),
        ];
    }
}
