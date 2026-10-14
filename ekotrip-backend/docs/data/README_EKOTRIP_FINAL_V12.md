# ÉKO TRIP Martinique — README développeur FINAL V1.2

## Référence canonique
Le fichier `EKOTRIP_FINAL_V1_2_EVENTS.xlsx` est la référence technique principale de cette version.

## Nouveauté V1.2 — Agenda / Événements
Le module Événements est intégré sans modifier les 383 fiches actives du catalogue.
Les événements sont stockés dans une structure séparée et peuvent référencer un organisateur ou un lieu existant via leurs IDs.

Onglets ajoutés :
- `79_EVENTS_SCHEMA_V1`
- `80_EVENTS_UX_V1`
- `81_EVENTS_RULES_V1`
- `82_EVENTS_MON_EKOTRIP_V1`
- `83_EVENTS_PRO_V1`
- `84_RECETTE_EVENTS_V1`

## Parcours utilisateur
Accès principal : `Découvrir > Événements`.

V1 :
- Aujourd'hui
- Ce week-end
- Cette semaine
- À venir
- Filtres date, zone, catégorie, gratuit/payant, famille, intérieur/extérieur
- Liste / carte
- Fiche événement
- Ajouter à Mon Ékotrip
- Réserver / billetterie / contact selon le vrai canal disponible

Aucun 6e onglet principal n'est ajouté.

## Cycle de vie
Statuts :
`DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `CANCELLED`, `POSTPONED`, `ENDED`, `ARCHIVED`.

Un événement expiré sort automatiquement des listes « À venir ».
Un événement annulé ou reporté conserve un statut explicite afin de ne pas disparaître silencieusement lorsqu'il a déjà été favorisé ou ajouté à une journée.

## Mon Ékotrip
Un événement est une **ancre horaire** :
- `start_at` et `end_at` ne sont jamais déplacés arbitrairement ;
- le générateur construit les autres étapes autour ;
- les conflits horaires doivent être signalés ;
- aucun temps de trajet n'est inventé ;
- si un événement est annulé ou reporté, l'étape est invalidée et une alternative peut être proposée.

## Badge éco
Un événement **n'hérite jamais automatiquement** du badge éco du lieu ou de l'organisateur.
Des engagements propres à l'événement peuvent être décrits s'ils sont documentés, mais ils ne créent pas automatiquement un badge public.

## Médias
`06_MEDIAS` est désormais générique :
- `target_type = FICHE | CIRCUIT | EVENT`
- `target_id = identifiant correspondant`

Les mêmes règles de droits s'appliquent aux événements :
`GOOGLE_PLACES`, `OPEN_LICENSE`, `PARTNER_UPLOAD`, `EKOTRIP_OWNED`.

## Espace professionnel
Un professionnel validé pourra :
- créer un événement en brouillon ;
- le soumettre ;
- ajouter des photos avec déclaration de droits ;
- modifier, dupliquer, reporter ou annuler ses événements ;
- voir des statistiques uniquement si elles sont réellement mesurées.

V1 : une création professionnelle n'est pas publiée instantanément par défaut.
La modération ÉKO TRIP reste requise.

## Réservation / billetterie
Les événements réutilisent les règles existantes :
- `NONE`
- `PARTNER_SITE`
- `DIRECT_CONTACT`
- `IN_APP` uniquement si l'infrastructure réelle est active.

Un clic vers une billetterie externe ne crée jamais un booking ÉKO TRIP.

## Sponsorisation
Une future mise en avant événementielle doit être configurable et clairement identifiée comme sponsorisée.
Elle ne doit pas être confondue avec le classement organique ou les recommandations IA.

## Recette
`84_RECETTE_EVENTS_V1` contient 20 scénarios à tester avant ouverture publique de l'Agenda.

## Priorité documentaire
1. `EKOTRIP_FINAL_V1_2_EVENTS.xlsx`
2. `README_EKOTRIP_FINAL_V12.pdf`
3. PDF fonctionnels détaillés déjà existants
