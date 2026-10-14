<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class CircuitStep extends Model
{
    protected $guarded = [];

    protected function casts(): array
    {
        return ['is_retained' => 'boolean'];
    }

    public function circuit(): BelongsTo
    {
        return $this->belongsTo(Circuit::class);
    }

    public function fiche(): BelongsTo
    {
        return $this->belongsTo(Fiche::class);
    }

    /** Étape servie au public : retenue et rattachée à une fiche publique. */
    public function scopeServable(Builder $query): Builder
    {
        return $query->where('is_retained', true)
            ->whereHas('fiche', fn (Builder $q) => $q->public());
    }
}
