-- Sécurité renforcée : politique de mot de passe paramétrable, expiration, historique, double authentification (TOTP).

INSERT INTO parametre_application (cle, valeur, description) VALUES
    ('securite.mdp.longueur_min',        '10',    'Longueur minimale du mot de passe'),
    ('securite.mdp.majuscule',           'true',  'Le mot de passe doit contenir une majuscule'),
    ('securite.mdp.minuscule',           'true',  'Le mot de passe doit contenir une minuscule'),
    ('securite.mdp.chiffre',             'true',  'Le mot de passe doit contenir un chiffre'),
    ('securite.mdp.special',             'false', 'Le mot de passe doit contenir un caractère spécial'),
    ('securite.mdp.expiration_jours',    '90',    'Durée de validité du mot de passe en jours (0 = sans expiration)'),
    ('securite.mdp.historique',          '5',     'Nombre de mots de passe précédents qui ne peuvent pas être réutilisés'),
    ('securite.connexion.echecs_max',    '3',     'Échecs consécutifs avant verrouillage du compte'),
    ('securite.deux_facteurs.obligatoire', 'false', 'Double authentification obligatoire pour tous les utilisateurs');

ALTER TABLE securite_utilisateur
    ADD COLUMN mdp_modifie_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN totp_secret        VARCHAR(200),         -- secret chiffré (AES-GCM), null si non activée
    ADD COLUMN totp_active_le     TIMESTAMPTZ,
    ADD COLUMN totp_dernier_pas   BIGINT;               -- dernier pas de temps accepté (anti-rejeu)

CREATE TABLE securite_mdp_historique (
    id           BIGSERIAL    PRIMARY KEY,
    login        VARCHAR(50)  NOT NULL REFERENCES securite_utilisateur (login) ON DELETE CASCADE,
    empreinte    VARCHAR(200) NOT NULL,
    cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_mdp_historique_login ON securite_mdp_historique (login, cree_le DESC);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
    ('SECURITE_POLITIQUE', 'Politique de sécurité', 'SECURITE', 4);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'SECURITE_POLITIQUE');
