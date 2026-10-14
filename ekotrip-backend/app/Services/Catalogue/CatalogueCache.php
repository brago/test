<?php

namespace App\Services\Catalogue;

use Closure;
use Illuminate\Support\Facades\Cache;

/**
 * Cache versionné du catalogue public : toute modification (import, back-office)
 * incrémente la version, ce qui invalide d'un coup cartes, listes et suggestions.
 */
class CatalogueCache
{
    private const VERSION_KEY = 'ekotrip:catalogue:version';

    public const TTL_SECONDS = 3600;

    public static function remember(string $key, Closure $callback): mixed
    {
        return Cache::remember(self::key($key), self::TTL_SECONDS, $callback);
    }

    public static function key(string $key): string
    {
        return 'ekotrip:catalogue:v'.self::version().':'.$key;
    }

    public static function flush(): void
    {
        Cache::forever(self::VERSION_KEY, self::version() + 1);
    }

    private static function version(): int
    {
        return (int) Cache::get(self::VERSION_KEY, 1);
    }
}
