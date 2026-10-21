-- Table des BIN du réseau GIM (fichier LIS_IN_BIN) : plages de numéros de carte de toutes les banques membres,
-- avec le pays d'émission. Elle sert à vérifier les BIN de la banque (paramétrage « BIN cartes ») et à repérer
-- les préfixes partagés entre plusieurs banques.
CREATE TABLE bin_gim (
    id        BIGSERIAL    PRIMARY KEY,
    prefixe   VARCHAR(11)  NOT NULL,          -- BIN annoncé par le fichier
    debut     VARCHAR(19)  NOT NULL,          -- premier numéro de carte de la plage
    fin       VARCHAR(19)  NOT NULL,          -- dernier numéro de carte de la plage
    pays      VARCHAR(3)   NOT NULL           -- code pays ISO numérique (384 = Côte d'Ivoire)
);
CREATE INDEX ix_bin_gim_debut6 ON bin_gim (left(debut, 6));

-- Historique des imports de la table.
CREATE TABLE bin_gim_import (
    id          BIGSERIAL    PRIMARY KEY,
    fichier     VARCHAR(200) NOT NULL,
    nb_plages   INTEGER      NOT NULL,
    importe_le  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    importe_par VARCHAR(50)
);
