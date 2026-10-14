<?php

namespace App\Services\Catalogue;

use App\Models\Media;
use Illuminate\Support\Collection;

/**
 * Résolution des médias publiables (78_MEDIAS_SOURCES_V11) :
 * ACTIVE + droits qualifiés, triés par priorité de source puis photo principale.
 * Aucun média → null : le client affiche le placeholder ÉKO TRIP de la catégorie.
 */
class MediaResolver
{
    /**
     * @param  list<string>  $targetIds
     * @return Collection<string, Collection<int, Media>> médias servables par target_id
     */
    public function forTargets(string $targetType, array $targetIds): Collection
    {
        if ($targetIds === []) {
            return collect();
        }

        return Media::query()->servable()
            ->where('target_type', $targetType)
            ->whereIn('target_id', $targetIds)
            ->get()
            ->sortBy(fn (Media $m) => [
                Media::SOURCE_PRIORITY[$m->source_type] ?? 9,
                $m->is_primary ? 0 : 1,
                $m->sort_order ?? PHP_INT_MAX,
            ])
            ->groupBy('target_id');
    }

    /** @param Collection<int, Media>|null $media */
    public static function cover(?Collection $media): ?array
    {
        if (! $media || $media->isEmpty()) {
            return null;
        }

        return self::present($media->firstWhere('is_primary', true) ?? $media->first());
    }

    public static function present(Media $media): array
    {
        return [
            'id' => $media->id,
            'role' => $media->media_role,
            'source_type' => $media->source_type,
            'storage_mode' => $media->storage_mode,
            'provider' => $media->provider,
            'external_asset_id' => $media->external_asset_id,
            'url_or_ref' => $media->asset_url_or_ref,
            'alt_text' => $media->alt_text,
            'attribution' => $media->attribution_required ? $media->attribution_text : null,
        ];
    }
}
