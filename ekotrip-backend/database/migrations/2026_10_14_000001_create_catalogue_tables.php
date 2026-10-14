<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('map_groups', function (Blueprint $table) {
            $table->id();
            $table->string('slug', 50)->unique();
            $table->string('label', 100);
            $table->string('icon', 50)->nullable();
            $table->string('color', 20)->nullable();
            $table->unsignedSmallInteger('sort_order')->default(0);
            $table->timestamps();
        });

        Schema::create('categories', function (Blueprint $table) {
            $table->id();
            $table->string('name', 100)->unique();
            $table->string('slug', 100)->unique();
            $table->unsignedSmallInteger('sort_order')->default(0);
            $table->foreignId('map_group_id')->nullable()->constrained()->nullOnDelete();
            $table->timestamps();
        });

        Schema::create('fiches', function (Blueprint $table) {
            // Identifiant stable du catalogue (HEB-001…) : jamais régénéré.
            $table->string('id', 20)->primary();
            $table->string('nom');
            $table->text('adresse')->nullable();
            $table->string('code_postal', 10)->nullable();
            $table->string('commune', 100)->nullable()->index();
            $table->string('categorie', 100)->nullable();

            // Publication
            $table->string('publication', 150)->nullable();
            $table->string('publication_status', 20)->index();
            $table->boolean('is_active')->default(false)->index();
            $table->string('content_type', 30)->nullable();

            // Géographie
            $table->boolean('show_on_map')->default(false);
            $table->decimal('latitude', 10, 7)->nullable();
            $table->decimal('longitude', 10, 7)->nullable();
            $table->string('precision_geo')->nullable();
            $table->string('geo_display_type', 30)->nullable();
            $table->string('geo_display_label', 100)->nullable();
            $table->boolean('geo_navigation_allowed')->default(false);

            // Contenu public
            $table->text('description')->nullable();
            $table->string('accroche_courte')->nullable();
            $table->string('telephone', 100)->nullable();
            $table->text('site_internet')->nullable();
            $table->text('google_maps')->nullable();
            $table->text('instagram')->nullable();
            $table->text('facebook')->nullable();
            $table->text('horaires')->nullable();
            $table->text('tarif_indicatif')->nullable();
            $table->text('services_equipements')->nullable();
            $table->text('engagements_eco')->nullable();
            $table->text('public_cible')->nullable();
            $table->string('duree_recommandee')->nullable();
            $table->string('budget', 50)->nullable();
            $table->string('budget_data_status', 10)->default('UNKNOWN');
            $table->text('ambiance')->nullable();
            $table->string('interieur_exterieur', 100)->nullable();
            $table->string('niveau_activite', 100)->nullable();
            $table->text('accessibilite')->nullable();
            $table->text('tags_ia')->nullable();

            // Réservation (aucune réservation interne en phase 1)
            $table->text('mode_reservation')->nullable();
            $table->text('url_reservation')->nullable();
            $table->boolean('reservable_ekotrip')->nullable();
            $table->string('statut_partenaire', 100)->nullable();

            // Éco : badge public dérivé uniquement d'une évaluation validée
            $table->string('eco_badge_public', 50)->nullable();

            // Interne / traçabilité — jamais exposé par l'API publique
            $table->decimal('score_ekotrip', 5, 2)->nullable();
            $table->string('niveau_ekotrip', 100)->nullable();
            $table->string('statut_evaluation', 150)->nullable();
            $table->text('id_activites_associees')->nullable();
            $table->text('activites_associees')->nullable();
            $table->text('source_principale')->nullable();
            $table->text('source_geolocalisation')->nullable();
            $table->date('date_derniere_verification')->nullable();
            $table->text('source_derniere_verification')->nullable();
            $table->string('statut_verification', 30)->nullable();
            $table->json('raw_placeholders')->nullable();

            // Libellés de catégories dénormalisés pour l'index FULLTEXT
            $table->string('search_categories')->nullable();
            $table->timestamps();

            $table->index(['latitude', 'longitude']);
            $table->fullText('nom', 'fiches_ft_nom');
            $table->fullText('commune', 'fiches_ft_commune');
            $table->fullText(['search_categories', 'tags_ia'], 'fiches_ft_tags');
            $table->fullText('description', 'fiches_ft_description');
        });

        Schema::create('category_fiche', function (Blueprint $table) {
            $table->string('fiche_id', 20);
            $table->foreignId('category_id')->constrained()->cascadeOnDelete();
            $table->boolean('is_primary')->default(false);
            $table->primary(['fiche_id', 'category_id']);
            $table->foreign('fiche_id')->references('id')->on('fiches')->cascadeOnDelete();
        });

        Schema::create('precision_geo_rules', function (Blueprint $table) {
            $table->id();
            $table->string('precision_geo')->unique();
            $table->string('geo_display_type', 30);
            $table->string('origin', 20)->default('IMPORT'); // IMPORT | ADMIN
            $table->timestamps();
        });

        Schema::create('fiche_pois', function (Blueprint $table) {
            $table->id();
            $table->string('fiche_id', 20);
            $table->string('source_sheet', 30);
            $table->string('type', 50);
            $table->unsignedSmallInteger('ordre')->default(0);
            $table->string('label');
            $table->string('commune', 100)->nullable();
            $table->text('localisation')->nullable();
            $table->text('note_dev')->nullable();
            $table->timestamps();
            $table->foreign('fiche_id')->references('id')->on('fiches')->cascadeOnDelete();
        });

        Schema::create('pro_activity_links', function (Blueprint $table) {
            $table->id();
            $table->string('pro_fiche_id', 20);
            $table->string('activity_fiche_id', 20);
            $table->string('statut_relation', 100)->nullable();
            $table->text('note_interne')->nullable();
            $table->boolean('show_in_app')->default(false);
            $table->timestamps();
            $table->unique(['pro_fiche_id', 'activity_fiche_id']);
            $table->foreign('pro_fiche_id')->references('id')->on('fiches')->cascadeOnDelete();
            $table->foreign('activity_fiche_id')->references('id')->on('fiches')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('pro_activity_links');
        Schema::dropIfExists('fiche_pois');
        Schema::dropIfExists('precision_geo_rules');
        Schema::dropIfExists('category_fiche');
        Schema::dropIfExists('fiches');
        Schema::dropIfExists('categories');
        Schema::dropIfExists('map_groups');
    }
};
