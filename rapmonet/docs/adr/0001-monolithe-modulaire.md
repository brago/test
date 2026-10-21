# ADR 0001 – Monolithe modulaire Spring Boot + Angular + PostgreSQL

- **Date** : 2026-10-21
- **Statut** : accepté

## Contexte

RAPMONET remplace MONETICA V2 (application client lourd, Crystal Reports).
L'application sert une équipe back-office restreinte, avec des traitements de
fichiers quotidiens et des rapprochements. L'équipe de développement est petite.

## Décision

- Backend : **Java 21 + Spring Boot 4**, en **monolithe modulaire** (Spring Modulith).
- Base : **PostgreSQL**, installée en local pour le développement ; schéma versionné par **Flyway**.
- Frontend : **Angular** (application monopage) avec Angular Material.
- Le frontend et le backend sont dans le même dépôt, dans `frontend/` et `backend/`.

## Conséquences

- Un seul livrable backend à déployer et superviser ; pas de coût réseau entre services.
- Les frontières entre modules sont vérifiées par un test (`ArchitectureModulaireTest`) :
  un module pourra être extrait en service séparé plus tard si nécessaire.
- Les tests d'intégration utilisent une base PostgreSQL réelle (`rapmonet_test`),
  locale en développement et fournie par un service PostgreSQL en intégration continue.
