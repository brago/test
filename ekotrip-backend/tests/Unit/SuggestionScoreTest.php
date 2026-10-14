<?php

use App\Services\Search\SuggestionIndex;

beforeEach(fn () => $this->index = new SuggestionIndex);

it('privilégie le début du libellé, puis le début de mot, puis la sous-chaîne', function () {
    expect($this->index->score('dist', 'distillerie depaz'))->toBe(3.0)
        ->and($this->index->score('depa', 'distillerie depaz'))->toBe(2.0)
        ->and($this->index->score('illerie', 'distillerie depaz'))->toBe(1.0);
});

it('tolère une faute de frappe simple', function () {
    expect($this->index->score('distilery', 'distillerie depaz'))->toBeGreaterThan(0.7)->toBeLessThan(1.0)
        ->and($this->index->score('randonee', 'bureau de la randonnee'))->toBeGreaterThan(0.7);
});

it('rejette un terme trop éloigné', function () {
    expect($this->index->score('xyzabc', 'distillerie depaz'))->toBe(0.0);
});
