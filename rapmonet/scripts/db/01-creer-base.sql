-- Création de la base et de l'utilisateur RAPMONET (environnement local).
-- À exécuter une seule fois avec un super-utilisateur PostgreSQL :
--   psql -U postgres -f scripts/db/01-creer-base.sql

CREATE ROLE rapmonet WITH LOGIN PASSWORD 'rapmonet';
CREATE DATABASE rapmonet OWNER rapmonet ENCODING 'UTF8' TEMPLATE template0;
-- Base dédiée aux tests automatisés (vidée et recréée par Flyway à chaque exécution).
CREATE DATABASE rapmonet_test OWNER rapmonet ENCODING 'UTF8' TEMPLATE template0;
