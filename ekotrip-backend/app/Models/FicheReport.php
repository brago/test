<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class FicheReport extends Model
{
    use HasUuids;

    public const STATUSES = ['NOUVEAU', 'EN_COURS', 'TRAITE', 'REJETE'];

    protected $guarded = [];

    protected function casts(): array
    {
        return ['resolved_at' => 'datetime'];
    }

    protected static function booted(): void
    {
        static::saving(function (FicheReport $report) {
            if ($report->isDirty('statut')) {
                $report->resolved_at = in_array($report->statut, ['TRAITE', 'REJETE'], true) ? now() : null;
            }
        });
    }

    public function fiche(): BelongsTo
    {
        return $this->belongsTo(Fiche::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
