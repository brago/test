<?php

namespace App\Services\Search;

use Illuminate\Database\Eloquent\Builder;

/**
 * Contrat de recherche du catalogue. L'API ne dépend que de cette interface :
 * l'implémentation MySQL (FULLTEXT + collation ai_ci) pourra être remplacée
 * par Meilisearch sans modifier contrôleurs ni ressources.
 */
interface SearchEngine
{
    /**
     * Restreint la requête (déjà limitée aux fiches publiques et filtrée)
     * aux résultats pertinents pour $term et la trie par pertinence décroissante
     * (Lot 1.5 §8 : nom > commune > catégorie / tags > description).
     */
    public function search(Builder $query, string $term): Builder;

    /**
     * Suggestions rapides, tolérantes aux fautes simples.
     *
     * @return list<array{type:string,id:?string,label:string,commune:?string,categorie:?string,score:float}>
     */
    public function suggest(string $term, int $limit): array;

    /** Invalide les index / caches propres au moteur. */
    public function flush(): void;
}
