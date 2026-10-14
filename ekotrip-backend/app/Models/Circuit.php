<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Circuit extends Model
{
    public $incrementing = false;

    protected $keyType = 'string';

    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'latitude_depart' => 'float',
            'longitude_depart' => 'float',
        ];
    }

    public function steps(): HasMany
    {
        return $this->hasMany(CircuitStep::class)->orderBy('day')->orderBy('position');
    }

    public function notes(): HasMany
    {
        return $this->hasMany(CircuitNote::class)->orderBy('position');
    }
}
