<?php

use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Concerns\WithImportedCatalogue;
use Tests\TestCase;

/*
| Les tests Feature tournent sur une vraie base MySQL 8 (`testing`) dans laquelle le
| classeur canonique est importé une fois par processus. Chaque test est isolé dans
| une transaction annulée à la fin (DatabaseTransactions). Les index FULLTEXT ne voient
| que les données validées : les tests de recherche portent donc sur le catalogue importé.
*/
pest()->extend(TestCase::class)
    ->use(WithImportedCatalogue::class, DatabaseTransactions::class)
    ->in('Feature');

pest()->extend(TestCase::class)->in('Unit');
