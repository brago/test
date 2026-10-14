<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class FichePoi extends Model
{
    protected $guarded = [];

    public function fiche(): BelongsTo
    {
        return $this->belongsTo(Fiche::class);
    }
}
