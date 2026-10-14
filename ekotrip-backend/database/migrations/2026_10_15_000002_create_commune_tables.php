<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Décision 9 : correspondance administrable « valeur brute de commune → communes officielles
 * + lieu-dit + zone ». Les données sources (fiches.commune) ne sont jamais modifiées.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('communes', function (Blueprint $table) {
            $table->string('code_insee', 5)->primary();
            $table->string('nom', 100)->unique();
            $table->string('slug', 100)->unique();
            $table->json('codes_postaux');
            $table->timestamps();
        });

        Schema::create('commune_mappings', function (Blueprint $table) {
            $table->id();
            // Comparaison exacte (binaire) : chaque écriture de la source est une entrée distincte.
            $table->string('raw_value')->collation('utf8mb4_bin')->unique();
            $table->string('type', 20); // EXACT | VARIANTE | LIEU_DIT | MULTI | ZONE | COMMUNE_ZONE | SANS_COMMUNE
            $table->string('lieu_dit')->nullable();
            $table->string('zone', 100)->nullable();
            $table->string('statut_validation', 20)->default('VALIDE'); // VALIDE | PROVISOIRE
            $table->text('decision')->nullable();
            $table->text('commentaire')->nullable();
            $table->string('origin', 10)->default('IMPORT'); // IMPORT | ADMIN
            $table->timestamps();
        });

        Schema::create('commune_commune_mapping', function (Blueprint $table) {
            $table->foreignId('commune_mapping_id')->constrained()->cascadeOnDelete();
            $table->string('commune_code_insee', 5);
            $table->primary(['commune_mapping_id', 'commune_code_insee']);
            $table->foreign('commune_code_insee')->references('code_insee')->on('communes')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('commune_commune_mapping');
        Schema::dropIfExists('commune_mappings');
        Schema::dropIfExists('communes');
    }
};
