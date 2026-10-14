<?php

namespace App\Models;

use App\Services\Catalogue\EcoBadgeResolver;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EcoProof extends Model
{
    public const STATUSES = ['PENDING', 'VALIDATED', 'REJECTED', 'REVOKED'];

    public const KINDS = ['SITE_OFFICIEL', 'DOCUMENT', 'LABEL', 'ECHANGE_PRO', 'VISITE', 'AUTRE'];

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'document_date' => 'date',
            'validated_at' => 'datetime',
        ];
    }

    protected static function booted(): void
    {
        static::saved(fn (EcoProof $p) => app(EcoBadgeResolver::class)->refresh($p->evaluation->fiche));
        static::deleted(fn (EcoProof $p) => app(EcoBadgeResolver::class)->refresh($p->evaluation->fiche));
    }

    public function evaluation(): BelongsTo
    {
        return $this->belongsTo(EcoEvaluation::class, 'eco_evaluation_id');
    }
}
