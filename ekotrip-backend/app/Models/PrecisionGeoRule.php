<?php

namespace App\Models;

use App\Services\Catalogue\GeoClassifier;
use Illuminate\Database\Eloquent\Model;

class PrecisionGeoRule extends Model
{
    protected $guarded = [];

    protected static function booted(): void
    {
        // Une règle modifiée en back-office s'applique immédiatement aux fiches concernées.
        static::saved(function (PrecisionGeoRule $rule) {
            if ($rule->origin !== 'ADMIN') {
                return;
            }
            $classifier = new GeoClassifier;
            Fiche::query()->where('precision_geo', $rule->precision_geo)->get()->each(function (Fiche $fiche) use ($classifier) {
                $fiche->applyGeoClassification($classifier);
                $fiche->save();
            });
        });
    }
}
