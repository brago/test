<?php

use App\Filament\Resources\CommuneMappings\Pages\EditCommuneMapping;
use App\Models\Commune;
use App\Models\CommuneMapping;
use App\Models\Fiche;
use App\Models\ImportRun;
use App\Models\User;
use App\Services\Import\CatalogueImporter;
use Illuminate\Support\Facades\DB;
use Livewire\Livewire;

// Décision 9 (15/10/2026) : correspondance des communes, sources inchangées.

function communeIds(string $code, string $extra = ''): array
{
    return collect(test()->getJson("/api/v1/fiches?per_page=100&communes[]={$code}{$extra}")->assertOk()->json('data'))->pluck('id')->all();
}

it('importe les 34 communes officielles et une correspondance pour chaque valeur brute', function () {
    expect(Commune::count())->toBe(34)
        ->and(CommuneMapping::count())->toBe(77)
        ->and(Commune::find('97209')->codes_postaux)->toBe(['97200', '97234']);

    $unmapped = Fiche::whereNotNull('commune')->whereNotIn('commune', CommuneMapping::pluck('raw_value'))->count();
    expect($unmapped)->toBe(0);
});

it('ne modifie jamais la valeur source de la commune', function () {
    expect(Fiche::find('HEB-064')->commune)->toBe('Trois-Îlets')
        ->and(Fiche::find('ACT-078')->commune)->toBe('Les Trois-Îlets');

    $this->getJson('/api/v1/fiches/HEB-064')->assertOk()
        ->assertJsonPath('data.commune', 'Trois-Îlets')
        ->assertJsonPath('data.rattachement.communes.0.code_insee', '97231');
});

it('filtre par commune officielle en regroupant variantes, lieux-dits et multi-communes', function () {
    $ids = communeIds('97231'); // Les Trois-Îlets

    expect($ids)->toContain('HEB-064')   // « Trois-Îlets » (variante)
        ->toContain('GAS-039')           // « Les Trois-Îlets (Anse Mitan) » (lieu-dit)
        ->toContain('NAT-028');          // « Ducos / Les Trois-Îlets » (multi)
    expect(communeIds('97207'))->toContain('NAT-028'); // aussi dans Ducos
});

it('applique les décisions de relecture', function () {
    // ACT-064 : provisoire, Les Anses-d'Arlet + La Trinité (lieu-dit Caravelle)
    expect(communeIds('97202'))->toContain('ACT-064')->and(communeIds('97230'))->toContain('ACT-064');
    $this->getJson('/api/v1/fiches/ACT-064')
        ->assertJsonPath('data.rattachement.lieu_dit', 'Caravelle');
    expect(CommuneMapping::where('raw_value', "Anses-d'Arlet / Caravelle")->value('statut_validation'))->toBe('PROVISOIRE');

    // PAT-080 : Fort-de-France + zone Nord ; NAT-021 : Le Morne-Rouge + zone Nord
    $this->getJson('/api/v1/fiches/PAT-080')->assertJsonPath('data.rattachement.zone', 'Nord');
    expect(communeIds('97209'))->toContain('PAT-080')->and(communeIds('97218'))->toContain('NAT-021');
    $this->getJson('/api/v1/fiches/NAT-021')->assertJsonPath('data.rattachement.zone', 'Nord');

    // NAT-020, ACT-007, PRO-028 : zone seule, dans aucune commune
    foreach (['NAT-020' => 'Nord Caraïbe', 'ACT-007' => 'Centre | Sud', 'PRO-028' => 'Activité mobile'] as $id => $zone) {
        $this->getJson("/api/v1/fiches/{$id}")
            ->assertJsonPath('data.rattachement.communes', [])
            ->assertJsonPath('data.rattachement.zone', $zone);
    }

    expect(CommuneMapping::whereIn('raw_value', ['Tartane (La Trinité)', "Grande Anse d'Arlet (Les Anses-d'Arlet)"])->pluck('statut_validation')->unique()->all())
        ->toBe(['VALIDE']);
});

it('signale les codes postaux incompatibles sans corriger, dont ACT-078 Mangofil', function () {
    $run = ImportRun::query()->where('dry_run', false)->latest('id')->first();
    $issue = $run->issues()->where('code', 'POSTAL_CODE_MISMATCH')->where('row_ref', 'ACT-078')->first();

    expect($issue)->not->toBeNull()
        ->and($issue->message)->toContain('97228')->toContain('Sainte-Luce')->toContain('Forêt de Montravail')
        ->and(Fiche::find('ACT-078')->code_postal)->toBe('97228');
});

it('détecte toute fiche dont le code postal ne correspond pas à la commune rattachée', function () {
    // Code postal de Sainte-Anne (97227) sur une fiche du Carbet : doit être signalé.
    DB::table('fiches')->where('id', 'HEB-007')->update(['code_postal' => '97227']);
    config(['ekotrip.import.workbook' => base_path('docs/data/EKOTRIP_FINAL.xlsx')]);

    $run = app(CatalogueImporter::class)->run(dryRun: true);

    // L'import repart de la source : seule ACT-078 est incompatible dans le classeur réel.
    expect($run->issues()->where('code', 'POSTAL_CODE_MISMATCH')->pluck('row_ref')->all())->toBe(['ACT-078']);
});

it('signale une valeur de commune sans correspondance', function () {
    $csv = tempnam(sys_get_temp_dir(), 'communes');
    $lines = file(config('ekotrip.import.communes_correspondance'));
    file_put_contents($csv, implode('', array_filter($lines, fn ($l) => ! str_starts_with($l, 'Trois-Îlets;'))));
    config(['ekotrip.import.communes_correspondance' => $csv]);
    CommuneMapping::where('raw_value', 'Trois-Îlets')->delete();

    $run = app(CatalogueImporter::class)->run(dryRun: true);

    expect($run->issues()->where('code', 'COMMUNE_UNMAPPED')->pluck('row_ref')->all())->toBe(['HEB-064']);
    unlink($csv);
});

it('rend la correspondance administrable sans que l’import ne l’écrase', function () {
    $admin = User::factory()->create();
    $admin->forceFill(['is_admin' => true])->save();
    $this->actingAs($admin);
    $mapping = CommuneMapping::where('raw_value', 'Nord Caraïbe')->first();

    Livewire::test(EditCommuneMapping::class, ['record' => $mapping->id])
        ->fillForm(['communes' => ['97233'], 'type' => 'COMMUNE_ZONE'])
        ->call('save')
        ->assertHasNoFormErrors();

    expect($mapping->refresh()->origin)->toBe('ADMIN')
        ->and(communeIds('97233'))->toContain('NAT-020');

    app(CatalogueImporter::class)->run();
    expect($mapping->refresh()->communes->pluck('code_insee')->all())->toBe(['97233']);
});

it('expose les communes officielles dans /communes et les suggestions', function () {
    $communes = collect($this->getJson('/api/v1/communes')->json('data'))->keyBy('code_insee');
    expect($communes['97223']['nom'])->toBe('Saint-Esprit')
        ->and($communes['97223']['fiches_count'])->toBe(0)
        ->and($communes['97231']['fiches_count'])->toBeGreaterThan(30);

    $suggest = collect($this->getJson('/api/v1/search/suggest?q='.urlencode('trois ilets'))->json('data'))->where('type', 'commune');
    expect($suggest->pluck('id')->all())->toContain('97231');
});
