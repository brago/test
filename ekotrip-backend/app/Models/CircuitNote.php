<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class CircuitNote extends Model
{
    public const KIND_HEBERGEMENT = 'HEBERGEMENT';

    public const KIND_PRECAUTION = 'PRECAUTION';

    protected $guarded = [];

    public function circuit(): BelongsTo
    {
        return $this->belongsTo(Circuit::class);
    }

    public function fiche(): BelongsTo
    {
        return $this->belongsTo(Fiche::class);
    }
}
