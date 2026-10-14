<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ProActivityLink extends Model
{
    protected $guarded = [];

    protected function casts(): array
    {
        return ['show_in_app' => 'boolean'];
    }

    public function pro(): BelongsTo
    {
        return $this->belongsTo(Fiche::class, 'pro_fiche_id');
    }

    public function activity(): BelongsTo
    {
        return $this->belongsTo(Fiche::class, 'activity_fiche_id');
    }

    /** Lien affichable : « Afficher dans app » = TRUE et les deux fiches publiques. */
    public function scopeDisplayable(Builder $query): Builder
    {
        return $query->where('show_in_app', true)
            ->whereHas('pro', fn (Builder $q) => $q->public())
            ->whereHas('activity', fn (Builder $q) => $q->public());
    }
}
