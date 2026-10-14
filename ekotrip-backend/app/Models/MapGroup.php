<?php

namespace App\Models;

use App\Services\Catalogue\CatalogueCache;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class MapGroup extends Model
{
    protected $guarded = [];

    protected static function booted(): void
    {
        static::saved(fn () => CatalogueCache::flush());
    }

    public function categories(): HasMany
    {
        return $this->hasMany(Category::class);
    }
}
