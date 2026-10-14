<?php

namespace App\Models;

use App\Services\Catalogue\CatalogueCache;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

class Category extends Model
{
    protected $guarded = [];

    protected static function booted(): void
    {
        static::saved(fn () => CatalogueCache::flush());
    }

    public function mapGroup(): BelongsTo
    {
        return $this->belongsTo(MapGroup::class);
    }

    public function fiches(): BelongsToMany
    {
        return $this->belongsToMany(Fiche::class)->withPivot('is_primary');
    }
}
