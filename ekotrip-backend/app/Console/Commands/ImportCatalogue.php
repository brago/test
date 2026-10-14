<?php

namespace App\Console\Commands;

use App\Models\ImportRun;
use App\Services\Import\CatalogueImporter;
use Illuminate\Console\Command;

class ImportCatalogue extends Command
{
    protected $signature = 'ekotrip:import
        {--dry-run : Simule l\'import sans rien enregistrer (le rapport est conservé)}
        {--details=WARNING : Niveau minimal des anomalies listées (ERROR, WARNING, INFO)}';

    protected $description = 'Importe le classeur canonique EKOTRIP_FINAL.xlsx et produit un rapport d\'import';

    public function handle(CatalogueImporter $importer): int
    {
        $this->info('Import de '.config('ekotrip.import.workbook').($this->option('dry-run') ? ' (simulation)' : ''));

        $run = $importer->run((bool) $this->option('dry-run'));
        $this->renderReport($run);

        return $run->status === 'SUCCESS' ? self::SUCCESS : self::FAILURE;
    }

    private function renderReport(ImportRun $run): void
    {
        $summary = $run->summary;

        $this->newLine();
        $this->line("<options=bold>Rapport d'import #{$run->id}</> — statut : ".($run->status === 'SUCCESS' ? '<info>SUCCESS</info>' : '<error>FAILED</error>'));
        $this->table(['Compteur', 'Valeur'], collect($summary['counters'] ?? [])->map(fn ($v, $k) => [$k, $v])->values()->all());
        $this->table(['Sévérité', 'Nombre'], collect($summary['issues_by_severity'] ?? [])->map(fn ($v, $k) => [$k, $v])->values()->all());
        $this->table(['Code', 'Nombre'], collect($summary['issues_by_code'] ?? [])->map(fn ($v, $k) => [$k, $v])->values()->all());

        $levels = ['ERROR' => 3, 'WARNING' => 2, 'INFO' => 1];
        $min = $levels[strtoupper((string) $this->option('details'))] ?? 2;
        $issues = $run->issues()->orderByRaw("FIELD(severity, 'ERROR', 'WARNING', 'INFO')")->orderBy('id')->get()
            ->filter(fn ($i) => ($levels[$i->severity] ?? 0) >= $min);

        if ($issues->isNotEmpty()) {
            $this->table(['Sévérité', 'Code', 'Réf.', 'Message'], $issues->map(fn ($i) => [$i->severity, $i->code, $i->row_ref, $i->message])->all());
        }
    }
}
