<?php

namespace App\Models;

use App\Services\Catalogue\CatalogueCache;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

/**
 * Valeur brute de `fiches.commune` → communes officielles + lieu-dit + zone.
 * Le filtre de l'API utilise les communes officielles ; l'affichage garde la valeur brute.
 * Une correspondance modifiée en back-office (origin ADMIN) n'est jamais écrasée par l'import.
 */
class CommuneMapping extends Model
{
    public const TYPES = ['EXACT', 'VARIANTE', 'LIEU_DIT', 'MULTI', 'ZONE', 'COMMUNE_ZONE', 'SANS_COMMUNE'];

    protected $guarded = [];

    protected static function booted(): void
    {
        static::saved(fn () => CatalogueCache::flush());
        static::deleted(fn () => CatalogueCache::flush());
    }

    public function communes(): BelongsToMany
    {
        return $this->belongsToMany(Commune::class, 'commune_commune_mapping', 'commune_mapping_id', 'commune_code_insee');
    }
}
