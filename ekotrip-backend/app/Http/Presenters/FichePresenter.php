<?php

namespace App\Http\Presenters;

use App\Models\Category;
use App\Models\CircuitStep;
use App\Models\Fiche;
use App\Models\FichePoi;
use App\Models\Media;
use App\Models\ProActivityLink;
use App\Services\Catalogue\MediaResolver;
use Illuminate\Support\Collection;

/**
 * Sérialisation publique d'une fiche : liste blanche explicite.
 * Les champs internes (score_ekotrip, niveau_ekotrip, statut_evaluation, sources,
 * id_activites_associees, raw_placeholders…) ne sont jamais exposés.
 * Une valeur inconnue reste null (« non renseigné »).
 */
class FichePresenter
{
    /** Mini-fiche / élément de liste. */
    public static function summary(Fiche $fiche, ?Collection $media = null): array
    {
        $primary = $fiche->primaryCategory();

        $data = [
            'id' => $fiche->id,
            'nom' => $fiche->nom,
            'commune' => $fiche->commune,
            'accroche_courte' => $fiche->accroche_courte,
            'categorie' => $primary ? self::category($primary) : null,
            'categories' => $fiche->categories->sortByDesc('pivot.is_primary')->map(fn (Category $c) => self::category($c))->values()->all(),
            'location' => self::location($fiche),
            'eco_badge' => $fiche->eco_badge_public,
            'cover' => MediaResolver::cover($media),
        ];

        if (isset($fiche->distance_km)) {
            // Distance à vol d'oiseau, uniquement pour le tri « Autour de moi ».
            $data['distance_km'] = round((float) $fiche->distance_km, 2);
        }

        return $data;
    }

    /** Fiche détaillée (Lot 1.6). */
    public static function detail(Fiche $fiche, Collection $media, Collection $relatedMedia): array
    {
        $hasCoordinates = $fiche->latitude !== null && $fiche->longitude !== null;

        return self::summary($fiche, $media) + [
            'adresse' => $fiche->adresse,
            'code_postal' => $fiche->code_postal,
            'description' => $fiche->description,
            'content_type' => $fiche->content_type,
            'contact' => [
                'telephone' => $fiche->telephone,
                'site_internet' => $fiche->site_internet,
                'instagram' => $fiche->instagram,
                'facebook' => $fiche->facebook,
            ],
            'infos_pratiques' => [
                'horaires' => $fiche->horaires,
                'tarif_indicatif' => $fiche->tarif_indicatif,
                'services_equipements' => $fiche->services_equipements,
                'public_cible' => $fiche->public_cible,
                'duree_recommandee' => $fiche->duree_recommandee,
                'budget' => $fiche->budget,
                'ambiance' => $fiche->ambiance,
                'interieur_exterieur' => $fiche->interieur_exterieur,
                'niveau_activite' => $fiche->niveau_activite,
                'accessibilite' => $fiche->accessibilite,
            ],
            'engagements_eco' => $fiche->engagements_eco,
            'actions' => [
                'itineraire' => $hasCoordinates && $fiche->geo_navigation_allowed,
                'appeler' => $fiche->telephone !== null,
                'site' => $fiche->site_internet !== null,
                // Réservation : aucun canal actif en phase 1 (Lot 1.6 §8 : pas de faux bouton).
                'reserver' => false,
            ],
            'media' => $media->map(fn (Media $m) => MediaResolver::present($m))->values()->all(),
            'points_interet' => $fiche->pois->map(fn (FichePoi $p) => [
                'type' => $p->type,
                'ordre' => $p->ordre,
                'label' => $p->label,
                'commune' => $p->commune,
                'localisation' => $p->localisation,
            ])->values()->all(),
            'offres_professionnels' => $fiche->activityProLinks->map(fn (ProActivityLink $l) => self::summary($l->pro, $relatedMedia->get($l->pro_fiche_id)))->values()->all(),
            'activites_proposees' => $fiche->proActivityLinks->map(fn (ProActivityLink $l) => self::summary($l->activity, $relatedMedia->get($l->activity_fiche_id)))->values()->all(),
            'circuits' => $fiche->circuitSteps->unique('circuit_id')->map(fn (CircuitStep $s) => [
                'id' => $s->circuit->id,
                'nom' => $s->circuit->nom,
            ])->values()->all(),
        ];
    }

    public static function location(Fiche $fiche): array
    {
        $hasCoordinates = $fiche->latitude !== null && $fiche->longitude !== null;

        return [
            'latitude' => $hasCoordinates ? $fiche->latitude : null,
            'longitude' => $hasCoordinates ? $fiche->longitude : null,
            'geo_display_type' => $fiche->geo_display_type,
            'geo_display_label' => $fiche->geo_display_label,
            'navigation_allowed' => $hasCoordinates && $fiche->geo_navigation_allowed,
            'on_map' => $fiche->isOnMap(),
        ];
    }

    public static function category(Category $category): array
    {
        return [
            'nom' => $category->name,
            'slug' => $category->slug,
            'groupe' => $category->mapGroup ? [
                'slug' => $category->mapGroup->slug,
                'label' => $category->mapGroup->label,
                'icon' => $category->mapGroup->icon,
                'color' => $category->mapGroup->color,
            ] : null,
        ];
    }
}
