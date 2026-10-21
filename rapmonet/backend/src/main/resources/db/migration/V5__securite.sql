-- Sécurité : menus (habilitations), profils, utilisateurs et piste d'audit.

-- Arbre des menus : chaque menu est une habilitation. Un menu ne peut être autorisé
-- que si son menu parent l'est aussi.
CREATE TABLE securite_menu (
    code         VARCHAR(50)  PRIMARY KEY,
    libelle      VARCHAR(200) NOT NULL,
    parent_code  VARCHAR(50)  REFERENCES securite_menu (code),
    rang         INTEGER      NOT NULL
);

CREATE TABLE securite_profil (
    code         VARCHAR(30)  PRIMARY KEY,
    libelle      VARCHAR(200) NOT NULL,
    description  VARCHAR(1000),
    systeme      BOOLEAN      NOT NULL DEFAULT FALSE,  -- profil protégé (non modifiable, non supprimable)
    modifie_le   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE securite_profil_menu (
    profil_code  VARCHAR(30) NOT NULL REFERENCES securite_profil (code) ON DELETE CASCADE,
    menu_code    VARCHAR(50) NOT NULL REFERENCES securite_menu (code),
    PRIMARY KEY (profil_code, menu_code)
);

CREATE TABLE securite_utilisateur (
    login               VARCHAR(50)  PRIMARY KEY,
    nom                 VARCHAR(200) NOT NULL,
    agence              VARCHAR(100),
    email               VARCHAR(200),
    profil_code         VARCHAR(30)  NOT NULL REFERENCES securite_profil (code),
    mot_de_passe        VARCHAR(200) NOT NULL,          -- empreinte BCrypt
    doit_changer_mdp    BOOLEAN      NOT NULL DEFAULT TRUE,
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    verrouille          BOOLEAN      NOT NULL DEFAULT FALSE,
    nb_echecs           INTEGER      NOT NULL DEFAULT 0,
    derniere_connexion  TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_le          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_evenement (
    id           BIGSERIAL    PRIMARY KEY,
    horodatage   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    login        VARCHAR(50),
    action       VARCHAR(50)  NOT NULL,
    objet        VARCHAR(200),
    detail       VARCHAR(2000),
    adresse_ip   VARCHAR(64),
    succes       BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_audit_horodatage ON audit_evenement (horodatage DESC);
CREATE INDEX idx_audit_login ON audit_evenement (login, horodatage DESC);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('ACCUEIL',               'Accueil',                          NULL,           1),
 ('SECURITE',              'Sécurité & habilitations',         NULL,           2),
 ('SECURITE_UTILISATEURS', 'Utilisateurs',                     'SECURITE',     1),
 ('SECURITE_PROFILS',      'Profils',                          'SECURITE',     2),
 ('SECURITE_AUDIT',        'Piste d''audit',                   'SECURITE',     3),
 ('PARAMETRAGE',           'Paramétrage',                      NULL,           3),
 ('PARAMETRAGE_LIS',       'Format LIS',                       'PARAMETRAGE',  1),
 ('CHARGEMENT',            'Chargement des données',           NULL,           4),
 ('CHARGEMENT_LIS',        'Fichiers LIS',                     'CHARGEMENT',   1),
 ('EXPLORATION',           'Exploration',                      NULL,           5),
 ('EXPLORATION_LIS',       'Données LIS',                      'EXPLORATION',  1),
 ('GAB',                   'GAB : appros & arrêtés',           NULL,           6),
 ('RAPPROCHEMENT',         'Rapprochements',                   NULL,           7),
 ('CHARGEBACK',            'Chargebacks',                      NULL,           8),
 ('REPORTING',             'Pilotage & réglementaire',         NULL,           9);

INSERT INTO securite_profil (code, libelle, description, systeme) VALUES
 ('ADMIN', 'Administrateur', 'Accès à tous les menus. Profil protégé.', TRUE),
 ('CONSULTATION', 'Consultation', 'Consultation des données chargées.', FALSE);

INSERT INTO securite_profil_menu (profil_code, menu_code)
SELECT 'ADMIN', code FROM securite_menu;

INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('CONSULTATION', 'ACCUEIL'), ('CONSULTATION', 'EXPLORATION'), ('CONSULTATION', 'EXPLORATION_LIS');

-- L'utilisateur « admin » est créé au premier démarrage (voir InitialisationAdministrateur).
