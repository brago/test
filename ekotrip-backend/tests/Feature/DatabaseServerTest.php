<?php

use Illuminate\Support\Facades\DB;

// Garde-fou : les tests doivent tourner sur le même moteur que la production (MySQL 8.x).

it('tourne sur MySQL avec la version minimale attendue', function () {
    $version = DB::selectOne('SELECT VERSION() AS v')->v;

    expect(DB::getDriverName())->toBe('mysql')
        ->and(str_contains(strtolower($version), 'mariadb'))->toBeFalse()
        ->and(version_compare(preg_replace('/[^0-9.].*$/', '', $version), env('EKOTRIP_MYSQL_MIN_VERSION', '8.0'), '>='))->toBeTrue();
});

it('utilise la collation insensible aux accents et à la casse', function () {
    $collation = DB::selectOne("SELECT TABLE_COLLATION AS c FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fiches'")->c;

    expect($collation)->toBe('utf8mb4_0900_ai_ci');
});
