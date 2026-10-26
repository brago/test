# RAPMONET

Back-office monétique web : chargement des fichiers (LIS du GIM, VSS de VISA,
E-journaux GAB, Amplitude), rapprochements, gestion des GAB, chargebacks et
reporting réglementaire. Version Java web améliorée de MONETICA V2.

| Partie | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Modulith, Flyway |
| Base de données | PostgreSQL 16+ |
| Frontend | Angular 21, Angular Material |

## Déploiement

Démonstration en ligne (Render) ou installation sur un serveur avec Docker : voir
[`docs/deploiement.md`](docs/deploiement.md). Jeu de données fictif pour tester : [`demo/`](demo/README.md).

## Prérequis

- **Java 21** (JDK)
- **Node.js** 22.22+ ou 24.15+ (npm inclus)
- **PostgreSQL** 16 ou plus récent
- Maven n'est pas nécessaire : le projet fournit `mvnw`.

## Installation locale

### 1. Base de données

Installer PostgreSQL (Windows : installeur EnterpriseDB ; Linux : `apt install postgresql`),
puis créer la base et l'utilisateur :

```bash
psql -U postgres -f scripts/db/01-creer-base.sql
```

Cela crée l'utilisateur `rapmonet` (mot de passe `rapmonet`), la base `rapmonet`
et la base `rapmonet_test` utilisée par les tests. Les tables sont créées
automatiquement par Flyway au démarrage du backend.

Pour utiliser d'autres paramètres de connexion, définir les variables
d'environnement `RAPMONET_DB_URL`, `RAPMONET_DB_USER`, `RAPMONET_DB_PASSWORD`.

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run        # Windows : mvnw.cmd spring-boot:run
```

- API : http://localhost:8080/api/systeme/info
- Documentation de l'API (Swagger) : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health

### 3. Frontend

```bash
cd frontend
npm install
npm start
```

Ouvrir http://localhost:4200. Les appels `/api` sont redirigés vers le backend
(fichier `proxy.conf.json`).

### 4. Première connexion

Au premier démarrage, le backend crée le compte **`admin`** (mot de passe **`admin`**, ou la
valeur de `RAPMONET_ADMIN_MOT_DE_PASSE`). Le changement de mot de passe est imposé à la
première connexion. L'administrateur crée ensuite les profils et les utilisateurs
(menu Sécurité & habilitations).

### Variables d'environnement (production)

| Variable | Rôle |
|---|---|
| `RAPMONET_DB_URL`, `RAPMONET_DB_USER`, `RAPMONET_DB_PASSWORD` | Connexion PostgreSQL |
| `RAPMONET_ADMIN_MOT_DE_PASSE` | Mot de passe initial du compte `admin` |
| `RAPMONET_CLE_EMPREINTE_CARTE` | Clé secrète des empreintes de numéros de carte (16 caractères minimum) |

## Tests

```bash
cd backend && ./mvnw verify      # tests unitaires + intégration (base rapmonet_test)
cd frontend && npx ng test --watch=false
```

Le test `ArchitectureModulaireTest` vérifie que les modules respectent leurs
frontières et génère leur documentation dans `backend/target/spring-modulith-docs`.

## Organisation

```
rapmonet/
├── backend/            API Spring Boot (un package par module métier)
├── frontend/           Application Angular
├── docs/
│   ├── architecture.md Vue d'ensemble et feuille de route
│   ├── adr/            Décisions d'architecture
│   └── specs/          Spécifications des formats et des règles métier
└── scripts/db/         Scripts d'installation de la base
```

Voir [docs/architecture.md](docs/architecture.md) pour le détail des modules
et le plan de livraison par lots.
