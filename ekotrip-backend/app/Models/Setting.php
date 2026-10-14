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

    /** Crée les paramètres manquants avec leur valeur par défaut (jamais d'écrasement). */
    public static function ensureDefaults(): void
    {
        foreach (config('ekotrip.settings_defaults') as $key => $value) {
            static::query()->firstOrCreate(['key' => $key], ['value' => $value]);
        }
    }

    /** Valeur administrable, sinon valeur par défaut de config/ekotrip.php. */
    public static function get(string $key): mixed
    {
        $setting = static::query()->find($key);

        return $setting?->value ?? config("ekotrip.settings_defaults.{$key}");
    }
}
