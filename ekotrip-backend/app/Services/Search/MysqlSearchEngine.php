<?php

namespace App\Services\Search;

use Illuminate\Database\Eloquent\Builder;

/**
 * Recherche MySQL 8 : colonnes en utf8mb4_0900_ai_ci (insensible accents / casse)
 * + index FULLTEXT (nom, commune, catégories + tags, description).
 */
class MysqlSearchEngine implements SearchEngine
{
    public function __construct(private readonly SuggestionIndex $suggestions) {}

    public function search(Builder $query, string $term): Builder
    {
        $term = trim(preg_replace('/\s+/u', ' ', $term));
        if ($term === '') {
            return $query;
        }

        $like = '%'.$this->escapeLike($term).'%';
        $prefix = $this->escapeLike($term).'%';
        $boolean = $this->booleanQuery($term);

        $relevance = 'CASE WHEN fiches.nom = ? THEN 100 WHEN fiches.nom LIKE ? THEN 60 WHEN fiches.nom LIKE ? THEN 40 ELSE 0 END'
            .' + CASE WHEN fiches.commune = ? THEN 30 WHEN fiches.commune LIKE ? THEN 15 ELSE 0 END';
        $bindings = [$term, $prefix, $like, $term, $like];

        if ($boolean !== '') {
            $relevance .= ' + MATCH(fiches.nom) AGAINST (? IN BOOLEAN MODE) * 10'
                .' + MATCH(fiches.commune) AGAINST (? IN BOOLEAN MODE) * 6'
                .' + MATCH(fiches.search_categories, fiches.tags_ia) AGAINST (? IN BOOLEAN MODE) * 4'
                .' + MATCH(fiches.description) AGAINST (? IN BOOLEAN MODE)';
            array_push($bindings, $boolean, $boolean, $boolean, $boolean);
        }

        $query->where(function (Builder $q) use ($like, $boolean) {
            $q->where('fiches.nom', 'like', $like)
                ->orWhere('fiches.commune', 'like', $like)
                ->orWhere('fiches.search_categories', 'like', $like);
            if ($boolean !== '') {
                $q->orWhereFullText('fiches.nom', $boolean, ['mode' => 'boolean'])
                    ->orWhereFullText('fiches.commune', $boolean, ['mode' => 'boolean'])
                    ->orWhereFullText(['fiches.search_categories', 'fiches.tags_ia'], $boolean, ['mode' => 'boolean'])
                    ->orWhereFullText('fiches.description', $boolean, ['mode' => 'boolean']);
            }
        });

        if ($query->getQuery()->columns === null) {
            $query->select('fiches.*');
        }

        return $query->selectRaw("({$relevance}) AS relevance", $bindings)
            ->reorder()
            ->orderByDesc('relevance')
            ->orderBy('fiches.nom');
    }

    public function suggest(string $term, int $limit): array
    {
        return $this->suggestions->suggest($term, $limit);
    }

    public function flush(): void
    {
        $this->suggestions->flush();
    }

    /**
     * Requête FULLTEXT booléenne : chaque mot (≥ 3 caractères, taille minimale InnoDB)
     * devient un préfixe obligatoire (« trois ilets » = trois* ET ilets*) ;
     * les opérateurs saisis par l'utilisateur sont neutralisés.
     */
    private function booleanQuery(string $term): string
    {
        $clean = preg_replace('/[+\-<>()~*"@\'’.,;:!?\/\\\\]+/u', ' ', $term);
        $words = array_filter(preg_split('/\s+/u', $clean), fn ($w) => mb_strlen($w) >= 3);

        return implode(' ', array_map(fn ($w) => '+'.$w.'*', $words));
    }

    private function escapeLike(string $value): string
    {
        return str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], $value);
    }
}
