# Paramètres de l'application

Écran *Paramétrage › Paramètres de l'application* (menu `PARAMETRAGE_APPLICATION`), classe
`parametrage.internal.ParametresApplication`, sur la table `parametre_application`.

- **Vue d'ensemble** de tous les paramètres, par groupe.
- **Modifiables ici** (valeur contrôlée, motif obligatoire, historique `parametre_historique`, audit
  `PARAMETRAGE_APPLICATION`) :

| Groupe | Paramètre | Contrôle |
|---|---|---|
| Application | `application.nom`, `application.devise` | 1 à 40 caractères ; 3 lettres majuscules |
| Journaux GAB | `ejournal.coupures` | 4 entiers de 0 à 100 000 (types de billet 1 à 4), au moins un non nul |
| Journaux GAB | `ejournal.ncr.format_date` | format de date java.time valide (aperçu affiché) |
| Activité des GAB | `gab.activite.plage_debut`, `plage_fin` | HH:mm, début avant fin |
| Activité des GAB | `inactivite_alerte_minutes`, `taux_refus_alerte`, `tentatives_min` | 30–1 440 ; 1–100 ; 1–1 000 |
| Chargebacks | `chargeback.delai_jours` | 1 à 365 |

- **Gérés par leur écran** (affichés en lecture, avec le lien) : `securite.*` (Politique de sécurité),
  `rapprochement.gab_lis.*` (Règles de rapprochement), `ejournal.batch.*` et `lis.batch.*` (écrans de chargement),
  `compense.*` (Commerçants et comptes de la compense). Une modification de ces clés depuis cet écran est refusée.

Tout nouveau paramètre réglable doit être ajouté au catalogue de `ParametresApplication` (type, bornes, groupe) ou
rattaché à l'écran qui le gère.
