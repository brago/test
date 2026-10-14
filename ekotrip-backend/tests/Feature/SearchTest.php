<?php

use App\Models\Fiche;
use App\Services\Search\SearchEngine;
use Illuminate\Database\Eloquent\Builder;

function searchIds(string $q, string $extra = ''): array
{
    return collect(test()->getJson('/api/v1/fiches?per_page=100&q='.urlencode($q).$extra)->assertOk()->json('data'))->pluck('id')->all();
}

it('trouve des résultats pertinents pour les termes de recette du Lot 1.4', function (string $q) {
    expect(searchIds($q))->not->toBeEmpty();
})->with(['Trois-Îlets', 'randonnée', 'spa', 'artisanat', 'distillerie']);

it('est insensible aux accents et à la casse', function () {
    expect(searchIds('trois-ilets'))->toBe(searchIds('TROIS-ÎLETS'))
        ->and(searchIds('randonnee'))->toBe(searchIds('Randonnée'))
        // Tiret ou espace : mêmes fiches (seul l'ordre peut varier).
        ->and(collect(searchIds('trois ilets'))->sort()->values()->all())->toBe(collect(searchIds('Trois-Îlets'))->sort()->values()->all());
});

it('classe la correspondance exacte du nom en premier', function () {
    expect(searchIds('Distillerie Depaz')[0])->toBe(
        Fiche::query()->public()->where('nom', 'Distillerie Depaz')->value('id')
    );
});

it('fait remonter les correspondances de nom avant celles de description', function () {
    $ids = searchIds('distillerie');
    $names = Fiche::whereIn('id', array_slice($ids, 0, 3))->pluck('nom');

    expect($names->every(fn ($n) => str_contains(mb_strtolower($n), 'distillerie')))->toBeTrue();
});

it('combine recherche et filtres', function () {
    $all = searchIds('anse');
    $nature = searchIds('anse', '&categories[]=nature');

    expect($nature)->not->toBeEmpty()
        ->and(count($nature))->toBeLessThan(count($all))
        ->and(array_diff($nature, $all))->toBeEmpty();
});

it('neutralise les opérateurs FULLTEXT saisis par l’utilisateur', function () {
    $this->getJson('/api/v1/fiches?q='.urlencode('+spa -"(*@<>~'))->assertOk();
});

it('renvoie une liste vide sans erreur pour une recherche sans résultat', function () {
    $this->getJson('/api/v1/fiches?q=zzzzqqqq')->assertOk()->assertJsonPath('meta.total', 0);
});

it('suggère malgré une faute de frappe', function () {
    $labels = collect($this->getJson('/api/v1/search/suggest?q=distilery')->assertOk()->json('data'))->pluck('label');

    expect($labels)->toContain('Distillerie Depaz');
});

it('suggère des communes', function () {
    $data = collect($this->getJson('/api/v1/search/suggest?q='.urlencode('sainte an'))->json('data'));

    expect($data->where('type', 'commune')->pluck('label'))->toContain('Sainte-Anne');
});

it('ne suggère rien sous le nombre minimal de caractères', function () {
    $this->getJson('/api/v1/search/suggest?q=a')->assertOk()->assertJsonCount(0, 'data');
});

it('passe par le contrat SearchEngine, remplaçable sans toucher à l’API', function () {
    $fake = new class implements SearchEngine
    {
        public function search(Builder $query, string $term): Builder
        {
            return $query->where('fiches.id', 'HEB-001');
        }

        public function suggest(string $term, int $limit): array
        {
            return [['type' => 'fiche', 'id' => 'HEB-001', 'label' => 'Villa Moina', 'commune' => null, 'categorie' => null, 'score' => 1.0]];
        }

        public function flush(): void {}
    };
    app()->instance(SearchEngine::class, $fake);

    expect(searchIds('peu importe'))->toBe(['HEB-001']);
    $this->getJson('/api/v1/search/suggest?q=xyz')->assertJsonPath('data.0.id', 'HEB-001');
});
