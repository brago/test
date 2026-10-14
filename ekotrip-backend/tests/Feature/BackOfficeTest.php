<?php

use App\Filament\Resources\EcoEvaluations\Pages\CreateEcoEvaluation;
use App\Filament\Resources\Fiches\Pages\EditFiche;
use App\Filament\Resources\Fiches\Pages\ListFiches;
use App\Filament\Resources\PrecisionGeoRules\Pages\EditPrecisionGeoRule;
use App\Models\Fiche;
use App\Models\FicheReport;
use App\Models\ImportRun;
use App\Models\PrecisionGeoRule;
use App\Models\User;
use Livewire\Livewire;

function admin(): User
{
    $user = User::factory()->create();
    $user->forceFill(['is_admin' => true])->save();

    return $user;
}

it('refuse le back-office aux invités et aux non-administrateurs', function () {
    $this->get('/admin/fiches')->assertRedirect('/admin/login');
    $this->actingAs(User::factory()->create())->get('/admin/fiches')->assertForbidden();
});

it('affiche chaque écran du back-office à un administrateur', function (string $url) {
    $this->actingAs(admin())->get(value($url))->assertOk();
})->with([
    '/admin',
    '/admin/fiches',
    '/admin/fiches/HEB-001/edit',
    '/admin/categories',
    '/admin/map-groups',
    '/admin/precision-geo-rules',
    '/admin/eco-evaluations',
    '/admin/eco-evaluations/create',
    '/admin/media',
    '/admin/media/create',
    '/admin/circuits',
    '/admin/circuits/CIR-001',
    '/admin/fiche-reports',
    '/admin/import-runs',
    fn () => '/admin/import-runs/'.ImportRun::query()->value('id'),
    '/admin/settings',
]);

it('ne permet ni de créer ni de supprimer une fiche maître', function () {
    $admin = admin();

    expect($admin->can('create', Fiche::class))->toBeFalse()
        ->and($admin->can('delete', Fiche::find('HEB-001')))->toBeFalse();
    $this->actingAs($admin)->get('/admin/fiches/create')->assertNotFound();
});

it('recherche une fiche par ID ou nom dans la vue de contrôle', function () {
    $this->actingAs(admin());

    Livewire::test(ListFiches::class)
        ->searchTable('Villa Moina')
        ->assertCanSeeTableRecords([Fiche::find('HEB-001')])
        ->assertCanNotSeeTableRecords([Fiche::find('HEB-002')]);
});

it('retire une fiche de la carte sans la supprimer', function () {
    $this->actingAs(admin());

    Livewire::test(EditFiche::class, ['record' => 'HEB-001'])
        ->fillForm(['show_on_map' => false])
        ->call('save')
        ->assertHasNoFormErrors();

    expect(Fiche::find('HEB-001'))->not->toBeNull();
    expect(collect($this->getJson('/api/v1/map/fiches')->json('features'))->pluck('id'))->not->toContain('HEB-001');
    $this->getJson('/api/v1/fiches/HEB-001')->assertOk();
});

it('recalcule la classe géo quand les coordonnées sont effacées', function () {
    $this->actingAs(admin());

    Livewire::test(EditFiche::class, ['record' => 'HEB-001'])
        ->fillForm(['latitude' => null, 'longitude' => null])
        ->call('save')
        ->assertHasNoFormErrors();

    $fiche = Fiche::find('HEB-001');
    expect($fiche->geo_display_type)->toBe('NON_NAVIGABLE')
        ->and($fiche->geo_navigation_allowed)->toBeFalse()
        ->and($fiche->isOnMap())->toBeFalse();
});

it('refuse des coordonnées hors Martinique', function () {
    $this->actingAs(admin());

    Livewire::test(EditFiche::class, ['record' => 'HEB-001'])
        ->fillForm(['latitude' => 48.85, 'longitude' => 2.35])
        ->call('save')
        ->assertHasFormErrors(['latitude', 'longitude']);
});

it('applique une règle de précision modifiée aux fiches concernées', function () {
    $this->actingAs(admin());
    $rule = PrecisionGeoRule::where('precision_geo', 'GPS exact – établissement')->first();
    $ids = Fiche::where('precision_geo', $rule->precision_geo)->where('content_type', 'POINT')->whereNotNull('latitude')->pluck('id');

    Livewire::test(EditPrecisionGeoRule::class, ['record' => $rule->id])
        ->fillForm(['geo_display_type' => 'INDICATIVE'])
        ->call('save')
        ->assertHasNoFormErrors();

    expect($rule->refresh()->origin)->toBe('ADMIN')
        ->and(Fiche::whereIn('id', $ids)->pluck('geo_display_type')->unique()->all())->toBe(['INDICATIVE']);
});

it('crée une évaluation validée avec preuve et en dérive le badge public', function () {
    $admin = admin();
    $this->actingAs($admin);

    Livewire::test(CreateEcoEvaluation::class)
        ->fillForm([
            'fiche_id' => 'HEB-002',
            'score' => 72,
            'status' => 'VALIDATED',
            'proofs' => [['kind' => 'LABEL', 'reference' => 'Clévacances', 'status' => 'VALIDATED']],
        ])
        ->call('create')
        ->assertHasNoFormErrors();

    $fiche = Fiche::find('HEB-002');
    expect($fiche->eco_badge_public)->toBe('Éko Responsable')
        ->and($fiche->ecoEvaluations()->first()->validated_by)->toBe($admin->id);
});

it('horodate la clôture d’un signalement', function () {
    $report = FicheReport::create(['fiche_id' => 'HEB-001', 'motif' => 'AUTRE', 'statut' => 'NOUVEAU']);

    $report->update(['statut' => 'TRAITE', 'decision' => 'Téléphone corrigé']);

    expect($report->refresh()->resolved_at)->not->toBeNull();
});

it('filtre réellement les fiches publiques et celles sans coordonnées', function () {
    $this->actingAs(admin());

    Livewire::test(ListFiches::class)
        ->filterTable('public')
        ->assertCountTableRecords(383)
        ->assertCanNotSeeTableRecords([Fiche::find('HEB-019')]);

    Livewire::test(ListFiches::class)
        ->filterTable('sans_coordonnees')
        ->assertCountTableRecords(Fiche::whereNull('latitude')->count());
});
