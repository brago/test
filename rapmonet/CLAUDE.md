# RAPMONET – consignes pour Claude

Back-office monétique web (remplace MONETICA V2). Lire `docs/architecture.md` avant toute tâche.

## Commandes

- Backend : `cd backend && ./mvnw verify` (nécessite PostgreSQL local avec la base `rapmonet_test`, voir `scripts/db/01-creer-base.sql`)
- Lancer le backend : `cd backend && ./mvnw spring-boot:run`
- Frontend : `cd frontend && npx ng test --watch=false && npx ng build`
- Lancer le frontend : `cd frontend && npm start` (proxy `/api` → `localhost:8080`)

## Règles

- Code métier, tables, API et messages en **français**.
- Un package par module sous `com.rapmonet` ; ne jamais appeler les sous-packages d'un autre module. `ArchitectureModulaireTest` doit rester vert.
- Schéma de base : **uniquement** via une nouvelle migration Flyway `V<n>__<sujet>.sql`. Ne jamais modifier une migration existante.
- Toute règle de lecture de fichier ou de rapprochement s'accompagne d'un test basé sur un fichier d'exemple anonymisé dans `backend/src/test/resources/fichiers/`.
- Ne jamais versionner de données bancaires réelles non anonymisées. Masquer les numéros de carte (6 premiers + 4 derniers chiffres) dans l'affichage et les journaux.
- Les spécifications fournies par l'utilisateur sont résumées dans `docs/specs/` (les PDF éditeurs sont confidentiels : ne pas les versionner).
- Fichiers réels (LIS, VSS…) : ils contiennent des numéros de carte. Ne jamais les copier dans le dépôt ; les tests utilisent des fichiers synthétiques (ex. `FichierLisSynthetique`).
- Paramétrage LIS : généré depuis la spec par `scripts/spec-lis/` ; toute correction passe par une nouvelle migration.
- Journaux GAB NCR : `ingestion.ejournal.LecteurJournalNcr` (sessions START/END, tickets retrait/solde, mode superviseur) ; format de date et valeur des coupures (`ejournal.coupures`, la ligne DENOMINATION du journal n'est pas fiable) dans `parametre_application` ; voir `docs/specs/ejournal.md`.
- Batch des journaux GAB : `ingestion.internal.BatchJournauxService` (dossier d'entrée → archive / rejet, historique `batch_execution`), déclenché par `PlanificationBatchJournaux` (vérification chaque minute, heure dans `parametre_application`). Les tests désactivent la planification (`application-test.yml`). Les dossiers `donnees/` contiennent des journaux réels : ignorés par git.
- GAB (lot 5) : module `gab` (paramétrage GAB / profils / BIN, appros, arrêtés, rapports PDF `RapportsGab`), voir `docs/specs/gab.md`. Les compteurs du journal NCR sont **par type de billet**, pas par cassette ; le profil GAB associe chaque cassette à une coupure et un type. Validation à quatre yeux : le valideur doit être différent de l'auteur de la saisie ou de la dernière modification (contrôlé côté serveur). Le module GAB lit les journaux via `ingestion.ConsultationJournaux` et fournit les coupures à l'ingestion via `ingestion.CoupuresGab`.
- Chargebacks (lot 7) : module `chargeback`, un dossier par ARN (`chargeback_dossier`), cycle relu dans les LIS via `ingestion.LitigesLis`, sens émission / acquisition selon `gab.BinsBanque` ; dossiers créés à l'événement `ingestion.FichiersLisIntegres`. Voir `docs/specs/chargeback.md`. Tests : `FichierLisSynthetique.creer(jjmmaa, numero, List<Operation>)` fabrique présentations, chargebacks et représentations.
- Pilotage (lot 8) : module `reporting` (`IndicateursService`) sur `ingestion.StatistiquesActivite` (agrégats LIS / journaux par entité et par mois) et `gab.ReferentielGab`. Graphiques : composant SVG `reporting/graphique-barres` (une série, barres fines, infobulle). Voir `docs/specs/reporting.md`.
- Javadoc : pas d'accolades ni de crochets dans les commentaires de classes et méthodes publiques (le générateur de documentation Spring Modulith les lit avec un analyseur JSON simplifié).
- Rapports VSS (VX / EP-747) : lecteur générique `ingestion.vss.LecteurRapportsVss` (colonnes déduites des titres, hiérarchie par indentation) ; voir `docs/specs/vss.md`.
- Frontend : composants autonomes (standalone), signaux, Angular Material ; un dossier par module sous `src/app/<module>/`, chargé en différé depuis `app.routes.ts`. Retirer l'entrée « À venir » du module quand il est réalisé.
- Sécurité : toute nouvelle API doit être déclarée dans `securite/internal/ConfigurationSecurite` avec le menu qui l'autorise (sinon refusée). Nouveau menu = migration `securite_menu` + entrée dans `frontend/src/app/core/menu.ts` + route avec `data.menu`. Tests : `@WithMockUser(authorities = ...)` et `.with(csrf())` sur les requêtes POST/PUT/DELETE.
- Tracer les actions significatives avec `JournalAudit` (module sécurité).
- Exports de listes : `commun.export` (`Tableau` + `ReponseExport`) ; le frontend télécharge via `core/telechargement.ts`.
- Mettre à jour le tableau « Feuille de route » de `docs/architecture.md` à la fin de chaque lot.
