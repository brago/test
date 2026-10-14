<?php

namespace App\Models;

use App\Services\Catalogue\CatalogueCache;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Fiche extends Model
{
    public const STATUS_PUBLISHED = 'published';

    public const STATUS_PENDING_REVIEW = 'pending_review';

    public const STATUS_UNPUBLISHED = 'unpublished';

    public const STATUS_ARCHIVED = 'archived';

    public const GEO_TYPES = ['EXACT', 'ACCESS_OR_DEPARTURE', 'INDICATIVE', 'NON_NAVIGABLE'];

    public $incrementing = false;

    protected $keyType = 'string';

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'is_active' => 'boolean',
            'show_on_map' => 'boolean',
            'geo_navigation_allowed' => 'boolean',
            'reservable_ekotrip' => 'boolean',
            'latitude' => 'float',
            'longitude' => 'float',
            'score_ekotrip' => 'float',
            'date_derniere_verification' => 'date',
            'raw_placeholders' => 'array',
        ];
    }

    protected static function booted(): void
    {
        static::saved(fn () => CatalogueCache::flush());
        static::deleted(fn () => CatalogueCache::flush());
    }

    public function categories(): BelongsToMany
    {
        return $this->belongsToMany(Category::class)->withPivot('is_primary');
    }

    public function pois(): HasMany
    {
        return $this->hasMany(FichePoi::class)->orderBy('source_sheet')->orderBy('ordre');
    }

    public function ecoEvaluations(): HasMany
    {
        return $this->hasMany(EcoEvaluation::class);
    }

    public function reports(): HasMany
    {
        return $this->hasMany(FicheReport::class);
    }

    public function circuitSteps(): HasMany
    {
        return $this->hasMany(CircuitStep::class);
    }

    /** Offres documentées d'un professionnel (fiche PRO-…). */
    public function proActivityLinks(): HasMany
    {
        return $this->hasMany(ProActivityLink::class, 'pro_fiche_id');
    }

    /** Professionnels liés à une activité (fiche ACT-…). */
    public function activityProLinks(): HasMany
    {
        return $this->hasMany(ProActivityLink::class, 'activity_fiche_id');
    }

    /**
     * Fiches visibles du public : active ET statut publié.
     * Seul point d'entrée autorisé pour l'API, la recherche et la carte.
     */
    public function scopePublic(Builder $query): Builder
    {
        return $query->where('fiches.is_active', true)
            ->where('fiches.publication_status', self::STATUS_PUBLISHED);
    }

    /** Fiches publiques éligibles à un marqueur cartographique. */
    public function scopeOnMap(Builder $query): Builder
    {
        $query->public()
            ->where('fiches.show_on_map', true)
            ->whereNotNull('fiches.latitude')
            ->whereNotNull('fiches.longitude');

        if (! config('ekotrip.map.include_non_navigable')) {
            $query->where('fiches.geo_display_type', '!=', 'NON_NAVIGABLE');
        }

        return $query;
    }

    public function isPublic(): bool
    {
        return $this->is_active && $this->publication_status === self::STATUS_PUBLISHED;
    }

    public function isOnMap(): bool
    {
        return $this->isPublic()
            && $this->show_on_map
            && $this->latitude !== null
            && $this->longitude !== null
            && (config('ekotrip.map.include_non_navigable') || $this->geo_display_type !== 'NON_NAVIGABLE');
    }

    public function primaryCategory(): ?Category
    {
        return $this->categories->firstWhere('pivot.is_primary', true) ?? $this->categories->first();
    }
}
