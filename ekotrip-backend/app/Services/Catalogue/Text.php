<?php

namespace App\Services\Catalogue;

use Illuminate\Support\Str;

class Text
{
    /** Majuscules sans accents, espaces normalisés : comparaison de libellés. */
    public static function normalize(?string $value): string
    {
        $value = Str::ascii((string) $value);
        $value = preg_replace('/\s+/', ' ', $value);

        return strtoupper(trim($value));
    }

    /** Minuscules sans accents : comparaison de recherche. */
    public static function fold(?string $value): string
    {
        return strtolower(self::normalize($value));
    }

    /** Valeur de cellule vide / espaces → null ; sinon chaîne nettoyée. */
    public static function clean(mixed $value): ?string
    {
        if ($value === null) {
            return null;
        }
        if (is_bool($value)) {
            return $value ? 'TRUE' : 'FALSE';
        }
        if ($value instanceof \DateTimeInterface) {
            return $value->format('Y-m-d');
        }
        $value = trim((string) $value);

        return $value === '' ? null : $value;
    }
}
