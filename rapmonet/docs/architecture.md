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
| Paramétrage | `parametrage` | Formats LIS (Fichier/Sous-fichier/TC/TCR/Attribut), formats VSS (Fichier EP/Sous-fichier/Record/Line/Attribut), codes réponse | §III |
| Ingestion | `ingestion` | Moteur de lecture générique piloté par le paramétrage ; chargement LIS, VSS, E-journaux, BKHIS, MOHISDET | §III |
| Exploration | `exploration` | Consultation, filtres, export PDF/Excel des données chargées, ACD | §IV |
| GAB | `gab` | Paramétrage GAB, profils GAB (cassettes), BIN ; approvisionnements, arrêtés, billetage, validation à quatre yeux, rapports | §V |
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
| Sécurité | Spring Security, session serveur + CSRF, BCrypt (ADR 0002) ; LDAP / AD envisageable |
| Audit | Journal applicatif des actions + historique des modifications |
| États PDF | OpenPDF pour les listes (`commun.export`) ; JasperReports pour les états mis en page (lots 5 et 8) |
| Export Excel | Apache POI |
| Tableaux de données (frontend) | Angular Material, AG Grid si besoin |
| Graphiques | SVG natif (composant `reporting/graphique-barres`), sans dépendance |

## Feuille de route

| Lot | Contenu | Statut |
|---|---|---|
| 0 | Socle : backend, frontend, base, tests, intégration continue | ✅ Fait |
| 1 | Sécurité : connexion, blocage après 3 échecs, utilisateurs, profils, audit ; exports Excel / PDF | ✅ Fait (voir ADR 0002) |
| 2 | Moteur de lecture générique + paramétrage et chargement LIS | ✅ Fait (voir `docs/specs/lis.md`), chargement automatique quotidien ✅, modification du paramétrage à l'écran ✅ |
| 3 | Rapports VSS de VISA (fichiers VX / EP-747) : chargement, consultation, export, paramétrage | ✅ Fait (voir `docs/specs/vss.md`) |
| 4 | E-journaux NCR ✅, intégration automatique quotidienne à 04:00 ✅ (voir `docs/specs/ejournal.md`) · Amplitude (BKHIS, MOHISDET) + ACD | Amplitude et ACD : attente d'exemples |
| 5 | GAB : paramétrage (GAB, profils, BIN), appros, arrêtés, validation à quatre yeux, rapports, activité des GAB | ✅ Fait (voir `docs/specs/gab.md`) ; arrêté périodique, analyse compte et encaisse : attente de description |
| 6 | Rapprochements + gestion des écarts : GAB ↔ LIS (retraits confrères) ✅ (voir `docs/specs/rapprochement-gab-lis.md`) · E-journal / Amplitude | Amplitude : attente des données |
| 7 | Chargebacks GIM émission / acquisition : cycle de vie par ARN, délai, régularisation, journal GAB | ✅ Fait (voir `docs/specs/chargeback.md`) ; libellés des motifs à fournir |
| 8 | KPI GAB / TPE / cartes (graphiques, tableau croisé), base trimestrielle réglementaire | ✅ Fait (voir `docs/specs/reporting.md`) ; formats BCEAO BP4 / VISA et PNB : attente des modèles |
| 9 | Compense TPE : référentiel commerçants (import de la macro), commission / TAF, macro comptable Amplitude, validation à quatre yeux | ✅ Fait (voir `docs/specs/compense-tpe.md`) |

## Améliorations prévues par rapport à MONETICA V2

1. Chargement automatique depuis un dossier surveillé (✅ journaux GAB et fichiers LIS, batch quotidien) ou SFTP (à faire : accès SFTP du GIM), refus des doublons (empreinte du fichier).
2. Lecture directe de BKHIS dans Amplitude si l'accès est possible (sinon import Excel).
3. Règles de rapprochement paramétrables, avec tolérances (✅ GAB ↔ LIS : écran, simulation, historique motivé).
4. Gestion des écarts : statut, responsable, commentaires, justificatifs (✅ GAB ↔ LIS).
5. Validation à quatre yeux généralisée (✅ appros et arrêtés GAB).
6. Prise en charge de Mastercard et d'autres marques de GAB que NCR.
7. Double authentification ✅, politique de mot de passe paramétrable ✅ (voir `docs/specs/securite-renforcee.md`), masquage des numéros de carte ✅.
8. PDF générés dans le navigateur, plus de gestion d'imprimante.
9. Tableau de bord en temps réel et alertes (✅ indicateurs mensuels ; ✅ alertes à l'accueil, voir `docs/specs/alertes.md` ; e-mail à venir).
