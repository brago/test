<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class ImportRun extends Model
{
    protected $guarded = [];

    protected function casts(): array
    {
        return [
            'dry_run' => 'boolean',
            'summary' => 'array',
            'started_at' => 'datetime',
            'finished_at' => 'datetime',
        ];
    }

    public function issues(): HasMany
    {
        return $this->hasMany(ImportIssue::class);
    }
}
