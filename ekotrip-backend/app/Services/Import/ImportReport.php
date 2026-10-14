<?php

namespace App\Services\Import;

use App\Models\ImportRun;

/**
 * Collecte compteurs et anomalies d'un import, puis les persiste
 * (import_runs / import_issues) hors de la transaction d'import,
 * afin que le rapport survive à un --dry-run ou à un échec.
 */
class ImportReport
{
    /** @var array<string,int> */
    private array $counters = [];

    /** @var list<array{severity:string,code:string,message:string,sheet:?string,row_ref:?string}> */
    private array $issues = [];

    private \DateTimeImmutable $startedAt;

    public function __construct(private readonly string $source, private readonly bool $dryRun)
    {
        $this->startedAt = new \DateTimeImmutable;
    }

    public function increment(string $key, int $by = 1): void
    {
        $this->counters[$key] = ($this->counters[$key] ?? 0) + $by;
    }

    public function count(string $key, int $value): void
    {
        $this->counters[$key] = $value;
    }

    public function error(string $code, string $message, ?string $sheet = null, ?string $rowRef = null): void
    {
        $this->add('ERROR', $code, $message, $sheet, $rowRef);
    }

    public function warning(string $code, string $message, ?string $sheet = null, ?string $rowRef = null): void
    {
        $this->add('WARNING', $code, $message, $sheet, $rowRef);
    }

    public function info(string $code, string $message, ?string $sheet = null, ?string $rowRef = null): void
    {
        $this->add('INFO', $code, $message, $sheet, $rowRef);
    }

    private function add(string $severity, string $code, string $message, ?string $sheet, ?string $rowRef): void
    {
        $this->issues[] = ['severity' => $severity, 'code' => $code, 'message' => $message, 'sheet' => $sheet, 'row_ref' => $rowRef];
    }

    public function finish(string $status): ImportRun
    {
        $bySeverity = array_count_values(array_column($this->issues, 'severity'));
        $byCode = array_count_values(array_column($this->issues, 'code'));
        ksort($this->counters);

        $run = ImportRun::query()->create([
            'source' => basename($this->source),
            'source_sha1' => is_file($this->source) ? sha1_file($this->source) : null,
            'dry_run' => $this->dryRun,
            'status' => $status,
            'summary' => [
                'counters' => $this->counters,
                'issues_by_severity' => $bySeverity,
                'issues_by_code' => $byCode,
            ],
            'started_at' => $this->startedAt,
            'finished_at' => now(),
        ]);

        foreach (array_chunk($this->issues, 500) as $chunk) {
            $now = now();
            $run->issues()->insert(array_map(fn ($i) => $i + [
                'import_run_id' => $run->id,
                'created_at' => $now,
                'updated_at' => $now,
            ], $chunk));
        }

        return $run;
    }
}
