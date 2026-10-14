<?php

namespace App\Models;

use App\Services\Catalogue\CatalogueCache;
use Illuminate\Database\Eloquent\Model;

class Setting extends Model
{
    public $incrementing = false;

    protected $primaryKey = 'key';

    protected $keyType = 'string';

    protected $guarded = [];

    protected function casts(): array
    {
        return ['value' => 'json'];
    }

    protected static function booted(): void
    {
        static::saved(fn () => CatalogueCache::flush());
    }

    /** Valeur administrable, sinon valeur par défaut de config/ekotrip.php. */
    public static function get(string $key): mixed
    {
        $setting = static::query()->find($key);

        return $setting?->value ?? config("ekotrip.settings_defaults.{$key}");
    }
}
