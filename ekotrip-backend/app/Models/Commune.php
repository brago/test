<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

/** Commune officielle (COG INSEE) avec ses codes postaux (La Poste). */
class Commune extends Model
{
    public $incrementing = false;

    protected $primaryKey = 'code_insee';

    protected $keyType = 'string';

    protected $guarded = [];

    protected function casts(): array
    {
        return ['codes_postaux' => 'array'];
    }

    public function mappings(): BelongsToMany
    {
        return $this->belongsToMany(CommuneMapping::class, 'commune_commune_mapping', 'commune_code_insee', 'commune_mapping_id');
    }
}
