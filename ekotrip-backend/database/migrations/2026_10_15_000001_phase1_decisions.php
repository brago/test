<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * Décisions de recette phase 1 (15/10/2026) :
 * - is_editorial : contenu éditorial public mais jamais sur la carte (décision 1) ;
 * - « Professionnels » rattaché au groupe carte des Activités (décision 5) :
 *   le groupe provisoire « professionnels » est retiré s'il n'a pas été modifié entre-temps.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('fiches', function (Blueprint $table) {
            $table->boolean('is_editorial')->default(false)->after('publication_status');
        });

        DB::table('fiches')->where('publication', 'CONTENU ÉDITORIAL')->update(['is_editorial' => true]);

        $provisional = DB::table('map_groups')->where('slug', 'professionnels')->first();
        $activities = DB::table('map_groups')->where('slug', 'bouger')->first();
        if ($provisional && $activities) {
            DB::table('categories')->where('map_group_id', $provisional->id)->where('name', 'Professionnels')
                ->update(['map_group_id' => $activities->id]);
            if (! DB::table('categories')->where('map_group_id', $provisional->id)->exists()) {
                DB::table('map_groups')->where('id', $provisional->id)->delete();
            }
        }
    }

    public function down(): void
    {
        Schema::table('fiches', fn (Blueprint $table) => $table->dropColumn('is_editorial'));
    }
};
