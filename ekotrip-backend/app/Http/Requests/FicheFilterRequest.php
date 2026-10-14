<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class FicheFilterRequest extends FormRequest
{
    public function rules(): array
    {
        return [
            'q' => ['nullable', 'string', 'max:100'],
            'categories' => ['nullable', 'array', 'max:20'],
            'categories.*' => ['string', 'max:100'],
            'groups' => ['nullable', 'array', 'max:20'],
            'groups.*' => ['string', 'max:50'],
            'communes' => ['nullable', 'array', 'max:40'],
            'communes.*' => ['string', 'max:100'],
            'near' => ['nullable', 'string', 'regex:/^-?\d{1,2}(\.\d+)?,-?\d{1,3}(\.\d+)?$/'],
            'radius' => ['nullable', 'numeric', 'min:0.1', 'max:100'],
            'bbox' => ['nullable', 'string', 'regex:/^-?\d{1,3}(\.\d+)?(,-?\d{1,3}(\.\d+)?){3}$/'],
            'eco_validated' => ['nullable', 'boolean'],
            'per_page' => ['nullable', 'integer', 'min:1', 'max:'.config('ekotrip.api.max_per_page')],
            'page' => ['nullable', 'integer', 'min:1'],
        ];
    }

    public function messages(): array
    {
        return [
            'near.regex' => 'near doit être au format « latitude,longitude ».',
            'bbox.regex' => 'bbox doit être au format « minLng,minLat,maxLng,maxLat ».',
        ];
    }
}
