<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('circuits', function (Blueprint $table) {
            $table->string('id', 20)->primary(); // CIR-001…
            $table->string('nom');
            $table->text('resume')->nullable();
            $table->string('duree_totale', 50)->nullable();
            $table->unsignedTinyInteger('nb_jours')->nullable();
            // Distance validée uniquement ; jamais calculée ni estimée à l'import.
            $table->string('distance_totale', 50)->nullable();
            $table->string('difficulte', 50)->nullable();
            $table->string('mode_deplacement', 100)->nullable();
            $table->string('point_depart')->nullable();
            $table->decimal('latitude_depart', 10, 7)->nullable();
            $table->decimal('longitude_depart', 10, 7)->nullable();
            $table->text('trace')->nullable();
            $table->text('photo_principale')->nullable();
            $table->text('communes')->nullable();
            $table->text('categories')->nullable();
            $table->unsignedSmallInteger('nb_etapes')->nullable();
            $table->string('statut', 50)->nullable();
            $table->text('notes')->nullable();
            $table->unsignedSmallInteger('sort_order')->default(0);
            $table->timestamps();
        });

        Schema::create('circuit_steps', function (Blueprint $table) {
            $table->id();
            $table->string('circuit_id', 20);
            $table->unsignedTinyInteger('day');
            $table->unsignedSmallInteger('position');
            $table->string('label');
            $table->string('commune', 100)->nullable();
            $table->string('fiche_id', 20)->nullable();
            $table->string('categorie', 100)->nullable();
            $table->string('sous_categorie', 100)->nullable();
            $table->text('note')->nullable();
            $table->string('decision', 100)->nullable();
            $table->text('consigne')->nullable();
            $table->string('statut_etape', 50)->nullable();
            // FALSE si la décision demande un retrait (ex. « REMPLACER / RETIRER »).
            $table->boolean('is_retained')->default(true);
            $table->timestamps();
            $table->foreign('circuit_id')->references('id')->on('circuits')->cascadeOnDelete();
            $table->foreign('fiche_id')->references('id')->on('fiches')->nullOnDelete();
            $table->unique(['circuit_id', 'day', 'position']);
        });

        Schema::create('circuit_notes', function (Blueprint $table) {
            $table->id();
            $table->string('circuit_id', 20);
            $table->string('kind', 20); // HEBERGEMENT | PRECAUTION
            $table->unsignedSmallInteger('position');
            $table->text('text');
            $table->string('fiche_id', 20)->nullable();
            $table->string('decision', 100)->nullable();
            $table->text('consigne')->nullable();
            $table->timestamps();
            $table->foreign('circuit_id')->references('id')->on('circuits')->cascadeOnDelete();
            $table->foreign('fiche_id')->references('id')->on('fiches')->nullOnDelete();
        });

        // 06_MEDIAS / EKOTRIP_DICTIONNAIRE_MEDIAS_V12 — table générique.
        Schema::create('media', function (Blueprint $table) {
            $table->string('id', 40)->primary(); // media_id
            $table->string('target_type', 10); // FICHE | CIRCUIT | EVENT
            $table->string('target_id', 40);
            $table->string('media_role', 20); // COVER | CARD | GALLERY | THUMBNAIL
            $table->string('source_type', 20); // GOOGLE_PLACES | OPEN_LICENSE | PARTNER_UPLOAD | EKOTRIP_OWNED
            $table->string('provider', 100)->nullable();
            $table->string('external_asset_id')->nullable();
            $table->text('source_page_url')->nullable();
            $table->text('asset_url_or_ref');
            $table->string('storage_mode', 20); // DYNAMIC_PROVIDER | EXTERNAL_ASSET | EKOTRIP_STORAGE
            $table->string('rights_status', 25); // PROVIDER_TERMS | LICENSE_VERIFIED | PARTNER_DECLARATION | OWNED | TO_VERIFY | BLOCKED
            $table->string('license_or_permission')->nullable();
            $table->text('license_url')->nullable();
            $table->string('author_or_rights_holder')->nullable();
            $table->string('attribution_text')->nullable();
            $table->boolean('attribution_required')->default(false);
            $table->boolean('commercial_use_allowed')->default(false);
            $table->string('proof_reference')->nullable();
            $table->date('permission_date')->nullable();
            $table->date('expires_at')->nullable();
            $table->boolean('is_primary')->default(false);
            $table->unsignedSmallInteger('sort_order')->nullable();
            $table->string('alt_text')->nullable();
            $table->string('publication_status', 10)->default('DRAFT'); // DRAFT | READY | ACTIVE | BLOCKED | EXPIRED
            $table->dateTime('last_checked_at')->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->index(['target_type', 'target_id']);
        });

        // Lot 1.1 §5 : évaluation éco et preuves, indépendantes du statut « Vérifié ».
        Schema::create('eco_evaluations', function (Blueprint $table) {
            $table->id();
            $table->string('fiche_id', 20);
            $table->decimal('score', 5, 2);
            $table->string('status', 20)->default('DRAFT'); // DRAFT | VALIDATED | REVOKED
            $table->text('criteria')->nullable();
            $table->foreignId('validated_by')->nullable()->constrained('users')->nullOnDelete();
            $table->dateTime('validated_at')->nullable();
            $table->dateTime('revoked_at')->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
            $table->foreign('fiche_id')->references('id')->on('fiches')->cascadeOnDelete();
        });

        Schema::create('eco_proofs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('eco_evaluation_id')->constrained()->cascadeOnDelete();
            $table->string('kind', 50); // SITE_OFFICIEL | DOCUMENT | LABEL | ECHANGE_PRO | VISITE | AUTRE
            $table->string('reference');
            $table->date('document_date')->nullable();
            $table->string('status', 20)->default('PENDING'); // PENDING | VALIDATED | REJECTED | REVOKED
            $table->dateTime('validated_at')->nullable();
            $table->text('notes')->nullable();
            $table->timestamps();
        });

        // 62_EXTENSIONS_SCHEMA — SIGNALEMENT.
        Schema::create('fiche_reports', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('fiche_id', 20);
            $table->foreignId('user_id')->nullable()->constrained()->nullOnDelete();
            $table->string('motif', 20);
            $table->string('commentaire', 1000)->nullable();
            $table->string('statut', 20)->default('NOUVEAU'); // NOUVEAU | EN_COURS | TRAITE | REJETE
            $table->text('decision')->nullable();
            $table->string('ip_hash', 64)->nullable();
            $table->dateTime('resolved_at')->nullable();
            $table->timestamps();
            $table->foreign('fiche_id')->references('id')->on('fiches')->cascadeOnDelete();
            $table->index('statut');
        });

        Schema::create('import_runs', function (Blueprint $table) {
            $table->id();
            $table->string('source');
            $table->string('source_sha1', 40)->nullable();
            $table->boolean('dry_run')->default(false);
            $table->string('status', 20); // RUNNING | SUCCESS | FAILED
            $table->json('summary')->nullable();
            $table->dateTime('started_at');
            $table->dateTime('finished_at')->nullable();
            $table->timestamps();
        });

        Schema::create('import_issues', function (Blueprint $table) {
            $table->id();
            $table->foreignId('import_run_id')->constrained()->cascadeOnDelete();
            $table->string('severity', 10); // ERROR | WARNING | INFO
            $table->string('code', 60);
            $table->string('sheet', 50)->nullable();
            $table->string('row_ref', 50)->nullable();
            $table->text('message');
            $table->timestamps();
            $table->index(['import_run_id', 'code']);
        });

        Schema::create('settings', function (Blueprint $table) {
            $table->string('key', 100)->primary();
            $table->json('value')->nullable();
            $table->timestamps();
        });

        Schema::table('users', function (Blueprint $table) {
            $table->boolean('is_admin')->default(false)->after('password');
        });
    }

    public function down(): void
    {
        Schema::table('users', fn (Blueprint $table) => $table->dropColumn('is_admin'));
        foreach (['settings', 'import_issues', 'import_runs', 'fiche_reports', 'eco_proofs', 'eco_evaluations', 'media', 'circuit_notes', 'circuit_steps', 'circuits'] as $t) {
            Schema::dropIfExists($t);
        }
    }
};
