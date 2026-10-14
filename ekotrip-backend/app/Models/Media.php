<?php

namespace App\Models;

use App\Services\Catalogue\CatalogueCache;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use InvalidArgumentException;

/**
 * 06_MEDIAS — média générique (FICHE / CIRCUIT / EVENT).
 * Seuls les médias ACTIVE, aux droits qualifiés et non expirés sont servis.
 */
class Media extends Model
{
    public const TARGET_TYPES = ['FICHE', 'CIRCUIT', 'EVENT'];

    public const SOURCE_TYPES = ['PARTNER_UPLOAD', 'EKOTRIP_OWNED', 'OPEN_LICENSE', 'GOOGLE_PLACES'];

    public const ROLES = ['COVER', 'CARD', 'GALLERY', 'THUMBNAIL'];

    public const STORAGE_MODES = ['DYNAMIC_PROVIDER', 'EXTERNAL_ASSET', 'EKOTRIP_STORAGE'];

    public const RIGHTS_STATUSES = ['PROVIDER_TERMS', 'LICENSE_VERIFIED', 'PARTNER_DECLARATION', 'OWNED', 'TO_VERIFY', 'BLOCKED'];

    public const PUBLICATION_STATUSES = ['DRAFT', 'READY', 'ACTIVE', 'BLOCKED', 'EXPIRED'];

    /** Ordre de priorité d'affichage (78_MEDIAS_SOURCES_V11). */
    public const SOURCE_PRIORITY = ['PARTNER_UPLOAD' => 1, 'EKOTRIP_OWNED' => 2, 'OPEN_LICENSE' => 3, 'GOOGLE_PLACES' => 4];

    public $incrementing = false;

    protected $keyType = 'string';

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'attribution_required' => 'boolean',
            'commercial_use_allowed' => 'boolean',
            'is_primary' => 'boolean',
            'permission_date' => 'date',
            'expires_at' => 'date',
            'last_checked_at' => 'datetime',
        ];
    }

    protected static function booted(): void
    {
        static::saving(function (Media $media) {
            foreach ([
                'target_type' => self::TARGET_TYPES,
                'source_type' => self::SOURCE_TYPES,
                'media_role' => self::ROLES,
                'storage_mode' => self::STORAGE_MODES,
                'rights_status' => self::RIGHTS_STATUSES,
                'publication_status' => self::PUBLICATION_STATUSES,
            ] as $field => $allowed) {
                if (! in_array($media->{$field}, $allowed, true)) {
                    throw new InvalidArgumentException("Valeur invalide pour {$field} : {$media->{$field}}");
                }
            }

            // Pas de stockage local des photos Google Places.
            if ($media->source_type === 'GOOGLE_PLACES' && $media->storage_mode !== 'DYNAMIC_PROVIDER') {
                throw new InvalidArgumentException('Une photo GOOGLE_PLACES doit rester DYNAMIC_PROVIDER.');
            }
        });

        static::saved(fn () => CatalogueCache::flush());
        static::deleted(fn () => CatalogueCache::flush());
    }

    public function scopeServable(Builder $query): Builder
    {
        return $query->where('publication_status', 'ACTIVE')
            ->whereNotIn('rights_status', ['TO_VERIFY', 'BLOCKED'])
            ->where(function (Builder $q) {
                // Les médias fournisseur relèvent des conditions du fournisseur ; les autres exigent l'usage commercial.
                $q->where('source_type', 'GOOGLE_PLACES')->orWhere('commercial_use_allowed', true);
            })
            ->where(fn (Builder $q) => $q->whereNull('expires_at')->orWhere('expires_at', '>=', now()->toDateString()));
    }

    public function scopeFor(Builder $query, string $targetType, string $targetId): Builder
    {
        return $query->where('target_type', $targetType)->where('target_id', $targetId);
    }
}
