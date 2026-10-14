<?php

namespace App\Services\Search;

use App\Models\Fiche;
use App\Services\Catalogue\CatalogueCache;
use App\Services\Catalogue\Text;

/**
 * Suggestions tolérantes aux fautes : la liste des noms et communes publics est
 * gardée en cache (Redis), la comparaison de proximité est faite en PHP.
 * Adapté au volume V1 (≈ 400 fiches) ; remplaçable via SearchEngine.
 */
class SuggestionIndex
{
    /**
     * @return list<array{type:string,id:?string,label:string,commune:?string,categorie:?string,score:float}>
     */
    public function suggest(string $term, int $limit): array
    {
        $needle = Text::fold($term);
        if (mb_strlen($needle) < (int) config('ekotrip.search.suggest_min_chars')) {
            return [];
        }

        $results = [];
        foreach ($this->entries() as $entry) {
            $score = $this->score($needle, $entry['folded']);
            if ($score > 0) {
                $results[] = ['score' => round($score, 3)] + $entry;
            }
        }

        usort($results, fn ($a, $b) => [$b['score'], $a['type'] === 'commune', $a['label']] <=> [$a['score'], $b['type'] === 'commune', $b['label']]);

        return array_map(fn ($r) => [
            'type' => $r['type'],
            'id' => $r['id'],
            'label' => $r['label'],
            'commune' => $r['commune'],
            'categorie' => $r['categorie'],
            'score' => $r['score'],
        ], array_slice($results, 0, $limit));
    }

    /**
     * 3 = début du libellé, 2 = début d'un mot, 1 = sous-chaîne,
     * ]0;1[ = similarité approchée (faute de frappe) au-dessus du seuil configuré.
     */
    public function score(string $needle, string $haystack): float
    {
        if (str_starts_with($haystack, $needle)) {
            return 3.0;
        }
        $words = preg_split('/[^a-z0-9]+/', $haystack, -1, PREG_SPLIT_NO_EMPTY);
        foreach ($words as $word) {
            if (str_starts_with($word, $needle)) {
                return 2.0;
            }
        }
        if (str_contains($haystack, $needle)) {
            return 1.0;
        }
        if (strlen($needle) < 3) {
            return 0.0;
        }

        // Tolérance aux fautes : on compare le terme au début de chaque mot (et du libellé)
        // de même longueur, pour accepter aussi une saisie incomplète.
        $best = 0.0;
        $len = strlen($needle);
        foreach (array_merge([$haystack], $words) as $candidate) {
            foreach (array_unique([substr($candidate, 0, $len), $candidate]) as $chunk) {
                if ($chunk === '' || abs(strlen($chunk) - $len) > 3) {
                    continue;
                }
                $similarity = 1 - levenshtein($needle, $chunk) / max($len, strlen($chunk));
                $best = max($best, $similarity);
            }
        }

        return $best >= (float) config('ekotrip.search.fuzzy_min_similarity') ? $best * 0.99 : 0.0;
    }

    /** @return list<array{type:string,id:?string,label:string,commune:?string,categorie:?string,folded:string}> */
    public function entries(): array
    {
        return CatalogueCache::remember(config('ekotrip.search.cache_key'), function () {
            $fiches = Fiche::query()->public()->orderBy('nom')->get(['id', 'nom', 'commune', 'categorie']);

            $entries = $fiches->map(fn (Fiche $f) => [
                'type' => 'fiche',
                'id' => $f->id,
                'label' => $f->nom,
                'commune' => $f->commune,
                'categorie' => $f->categorie,
                'folded' => Text::fold($f->nom),
            ])->all();

            $communes = $fiches->pluck('commune')->filter()->unique(fn ($c) => Text::fold($c));
            foreach ($communes as $commune) {
                $entries[] = ['type' => 'commune', 'id' => null, 'label' => $commune, 'commune' => $commune, 'categorie' => null, 'folded' => Text::fold($commune)];
            }

            return array_values($entries);
        });
    }

    public function flush(): void
    {
        CatalogueCache::flush();
    }
}
