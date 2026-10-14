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

    public function fiche(): BelongsTo
    {
        return $this->belongsTo(Fiche::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
