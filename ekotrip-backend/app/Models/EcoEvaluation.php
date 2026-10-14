<?php

namespace App\Models;

use App\Services\Catalogue\EcoBadgeResolver;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class EcoEvaluation extends Model
{
    public const STATUSES = ['DRAFT', 'VALIDATED', 'REVOKED'];

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'score' => 'float',
            'validated_at' => 'datetime',
            'revoked_at' => 'datetime',
        ];
    }

    protected static function booted(): void
    {
        static::saved(fn (EcoEvaluation $e) => app(EcoBadgeResolver::class)->refresh($e->fiche));
        static::deleted(fn (EcoEvaluation $e) => app(EcoBadgeResolver::class)->refresh($e->fiche));
    }

    public function fiche(): BelongsTo
    {
        return $this->belongsTo(Fiche::class);
    }

    public function proofs(): HasMany
    {
        return $this->hasMany(EcoProof::class);
    }
}
