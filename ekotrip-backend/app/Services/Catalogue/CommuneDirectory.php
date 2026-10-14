<?php

namespace App\Services\Catalogue;

use App\Models\Commune;
use App\Models\CommuneMapping;

/**
 * Accès en cache à la correspondance des communes (décision 9).
 */
class CommuneDirectory
{
    /** @return array<string, array{communes: list<array{code_insee:string,nom:string}>, lieu_dit:?string, zone:?string}> */
    public function mappings(): array
    {
        return CatalogueCache::remember('communes:mappings', fn () => CommuneMapping::query()
            ->with('communes')
            ->get()
            ->mapWithKeys(fn (CommuneMapping $m) => [$m->raw_value => [
                'communes' => $m->communes->sortBy('nom')->map(fn (Commune $c) => ['code_insee' => $c->code_insee, 'nom' => $c->nom])->values()->all(),
                'lieu_dit' => $m->lieu_dit,
                'zone' => $m->zone,
            ]])->all());
    }

    /** Rattachement officiel d'une valeur brute ; null si la valeur n'a pas (encore) de correspondance. */
    public function forRaw(?string $raw): ?array
    {
        return $raw === null ? null : ($this->mappings()[$raw] ?? null);
    }

    /**
     * Valeurs brutes rattachées à au moins une des communes données.
     *
     * @param  list<string>  $codesInsee
     * @return list<string>
     */
    public function rawValuesFor(array $codesInsee): array
    {
        return array_keys(array_filter(
            $this->mappings(),
            fn ($m) => array_intersect(array_column($m['communes'], 'code_insee'), $codesInsee) !== [],
        ));
    }
}
