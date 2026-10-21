# Architecture de RAPMONET

## Principe

Un **monolithe modulaire** : une seule application Spring Boot, découpée en
modules métier aux frontières vérifiées automatiquement (Spring Modulith).
Un module n'utilise que l'API publique d'un autre module (classes du package
racine du module) ; ses sous-packages (`internal`, `web`, `persistance`…) lui
sont privés. Les modules communiquent de préférence par **événements**
(ex. « fichier LIS chargé » → déclenche le rapprochement).

```
Angular (SPA) ──REST/JSON──> Spring Boot ──> PostgreSQL
                                  │
                                  └── dépôt de fichiers (dossier surveillé / SFTP)
```

## Modules

| Module | Package | Rôle | Écrans MONETICA V2 couverts |
|---|---|---|---|
| Sécurité | `securite` | Utilisateurs, profils, menus autorisés, mots de passe, piste d'audit | §II |
| Paramétrage | `parametrage` | Formats LIS (Fichier/Sous-fichier/TC/TCR/Attribut), formats VSS (Fichier EP/Sous-fichier/Record/Line/Attribut), codes réponse, GAB, profils GAB, BIN | §III, §V |
| Ingestion | `ingestion` | Moteur de lecture générique piloté par le paramétrage ; chargement LIS, VSS, E-journaux, BKHIS, MOHISDET | §III |
| Exploration | `exploration` | Consultation, filtres, export PDF/Excel des données chargées, ACD | §IV |
| GAB | `gab` | Approvisionnements, arrêtés, billetage, validation à quatre yeux | §V |
| Rapprochement | `rapprochement` | Moteur de règles ; LIS/SBA, E-journal/SBA, LIS/VSS, comptes d'activité ; gestion des écarts | §VI A–D |
| Chargeback | `chargeback` | Suivi émission et acquisition (ARN/RRN) | §VI E |
| Reporting | `reporting` | KPI GAB/TPE/Carte, tableaux croisés, BCEAO, VISA Issuing/Acquiring | §VII |
| Commun | `commun` | Erreurs HTTP, exports, utilitaires partagés (module ouvert) | – |
| Système | `systeme` | Informations sur l'application | – |

## Conventions

- **Langue** : le code métier est en français (noms de classes, tables, API),
  comme le vocabulaire des utilisateurs (arrêté, appro, rapprochement…).
- **Base** : schéma géré **uniquement** par Flyway (`db/migration/V<n>__<sujet>.sql`).
  Hibernate est en mode `validate`. Une migration publiée n'est jamais modifiée.
- **API** : REST sous `/api/<module>/…`, erreurs au format Problem Details (RFC 9457).
- **Données sensibles** : numéros de carte masqués à l'affichage et dans les
  journaux (6 premiers + 4 derniers chiffres), conformément à PCI-DSS.
- **Tests** : chaque règle de lecture ou de rapprochement est couverte par un
  test basé sur un fichier d'exemple anonymisé (`src/test/resources/fichiers/`).

## Choix techniques

| Besoin | Choix |
|---|---|
| Traitements de fichiers volumineux | Spring Batch (à introduire au lot 2) |
| Sécurité | Spring Security + JWT (lot 1), Keycloak envisageable plus tard |
| Audit | Journal applicatif des actions + historique des modifications |
| États PDF | JasperReports (remplace Crystal Reports) |
| Export Excel | Apache POI |
| Tableaux de données (frontend) | Angular Material, AG Grid si besoin |
| Graphiques | ECharts |

## Feuille de route

| Lot | Contenu | Statut |
|---|---|---|
| 0 | Socle : backend, frontend, base, tests, intégration continue | ✅ Fait |
| 1 | Sécurité : connexion, blocage après 3 échecs, utilisateurs, profils, audit | À faire |
| 2 | Moteur de lecture générique + paramétrage et chargement LIS | ✅ Fait (voir `docs/specs/lis.md`) ; écrans de modification du paramétrage à venir |
| 3 | VSS de VISA | Attente spec VSS |
| 4 | E-journaux NCR + Amplitude (BKHIS, MOHISDET) + ACD | Attente specs |
| 5 | GAB : paramétrage, appros, arrêtés, validation, rapports | À faire |
| 6 | Rapprochements + gestion des écarts | Attente règles |
| 7 | Chargebacks | À faire |
| 8 | KPI, BCEAO, VISA | Attente modèles d'états |

## Améliorations prévues par rapport à MONETICA V2

1. Chargement automatique depuis un dossier surveillé ou SFTP, refus des doublons (empreinte du fichier).
2. Lecture directe de BKHIS dans Amplitude si l'accès est possible (sinon import Excel).
3. Règles de rapprochement paramétrables, avec tolérances.
4. Gestion des écarts : statut, responsable, commentaires, justificatifs.
5. Validation à quatre yeux généralisée.
6. Prise en charge de Mastercard et d'autres marques de GAB que NCR.
7. Double authentification, politique de mot de passe, masquage des numéros de carte.
8. PDF générés dans le navigateur, plus de gestion d'imprimante.
9. Tableau de bord en temps réel et alertes.
