<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class FicheReportRequest extends FormRequest
{
    public function rules(): array
    {
        return [
            'motif' => ['required', 'string', Rule::in(config('ekotrip.reports.motifs'))],
            'commentaire' => ['nullable', 'string', 'max:1000'],
        ];
    }
}
