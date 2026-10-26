# Déploiement

L'application tient dans **une seule image Docker** (écrans Angular servis par le backend Spring Boot) plus une base
PostgreSQL. Fichiers : `Dockerfile`, `docker-compose.yml`, `.env.exemple` (dossier `rapmonet/`) et `render.yaml`
(racine du dépôt).

> **Données réelles** : ne jamais charger de fichiers bancaires réels (LIS, journaux GAB, VSS…) sur un hébergeur
> externe. L'hébergement de démonstration ne reçoit que le jeu fictif du dossier `demo/`. La production s'installe sur
> un serveur de la banque (option 2).

## Option 1 – Démonstration en ligne sur Render (gratuit, environ 15 minutes)

1. Créer un compte sur https://render.com (bouton « GitHub » pour se connecter avec le compte GitHub).
2. *New* › *Blueprint* › choisir le dépôt `brago/test` (autoriser Render à le lire), branche
   `claude/confident-thompson-5vua28`. Render lit `render.yaml` et propose la base `rapmonet-demo-base` et le service
   `rapmonet-demo` : *Apply*.
3. Attendre la fin de la construction (10 à 15 minutes la première fois), puis ouvrir l'adresse
   `https://rapmonet-demo-….onrender.com` affichée par Render.
4. Mot de passe initial de `admin` : service `rapmonet-demo` › *Environment* › `RAPMONET_ADMIN_MOT_DE_PASSE`
   (valeur générée par Render, bouton œil pour l'afficher). Il est à changer à la première connexion.
5. Charger les fichiers du dossier `demo/` (voir `demo/README.md`).

Limites de l'offre gratuite : le service s'endort après 15 minutes sans visite (premier affichage ensuite : environ
1 minute), 512 Mo de mémoire, la base gratuite expire au bout de 30 jours. Chaque nouveau commit sur la branche
redéploie automatiquement.

## Option 2 – Serveur de la banque (Docker)

Prérequis : Docker et Docker Compose sur un serveur Linux (ou Windows Server avec Docker Desktop).

```bash
git clone -b claude/confident-thompson-5vua28 https://github.com/brago/test.git
cd test/rapmonet
cp .env.exemple .env      # puis remplacer chaque valeur par une valeur longue et aléatoire
docker compose up -d --build
```

Ouvrir `http://<serveur>:8080`, compte `admin`, mot de passe `RAPMONET_ADMIN_MOT_DE_PASSE` du fichier `.env`.

- **HTTPS** : placer l'application derrière le reverse proxy de la banque (IIS, Nginx, F5…) ; les en-têtes
  `X-Forwarded-*` sont pris en compte.
- **Sauvegarde** : `docker compose exec base pg_dump -U rapmonet rapmonet > rapmonet_AAAAMMJJ.sql` (à planifier
  chaque jour et à conserver hors du serveur).
- **Dossiers des traitements automatiques** (journaux GAB, LIS) : volume `donnees` monté sur `/app/donnees`.
- **Mise à jour** : `git pull && docker compose up -d --build` (les migrations de base s'appliquent au démarrage).
- **Secrets** : `RAPMONET_CLE_CHIFFREMENT` ne doit plus changer une fois la double authentification utilisée.
