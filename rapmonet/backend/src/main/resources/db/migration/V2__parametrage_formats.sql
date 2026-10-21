-- Paramétrage des formats de fichiers à enregistrements de longueur fixe (LIS du GIM, puis VSS…).
-- Hiérarchie reprise de MONETICA V2 : Fichier → Sous-fichier → TC → TCR → Attributs.

-- Dictionnaire des attributs (informations unitaires contenues dans les TCR).
CREATE TABLE fmt_attribut (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(60)  NOT NULL UNIQUE,
    libelle      VARCHAR(200) NOT NULL,
    taille       INTEGER      NOT NULL CHECK (taille > 0),
    pad          VARCHAR(20)  NOT NULL DEFAULT 'ESPACE_DROITE',  -- ESPACE_DROITE, ZERO_GAUCHE, ESPACE_GAUCHE
    decimales    INTEGER      NOT NULL DEFAULT 0 CHECK (decimales >= 0),
    type_donnee  VARCHAR(30)  NOT NULL,                          -- voir TypeDonnee.java
    specifique   BOOLEAN      NOT NULL DEFAULT FALSE,            -- vrai = valeur enregistrée avec la transaction
    confidentiel BOOLEAN      NOT NULL DEFAULT FALSE,            -- vrai = numéro de carte : masqué avant stockage
    description  VARCHAR(1000)
);

-- Transaction Code : un type d'enregistrement du fichier.
CREATE TABLE fmt_tc (
    id           BIGSERIAL PRIMARY KEY,
    famille      VARCHAR(20)  NOT NULL,                          -- LIS, VSS…
    code         VARCHAR(10)  NOT NULL,
    libelle      VARCHAR(200) NOT NULL,
    description  VARCHAR(1000),
    longueur     INTEGER      NOT NULL,
    nb_tcr       INTEGER      NOT NULL DEFAULT 1,
    UNIQUE (famille, code)
);

-- Transaction Code Record : une ligne d'un TC (TCR 0, 1, 2…).
CREATE TABLE fmt_tcr (
    id           BIGSERIAL PRIMARY KEY,
    tc_id        BIGINT       NOT NULL REFERENCES fmt_tc (id) ON DELETE CASCADE,
    code         VARCHAR(10)  NOT NULL,
    libelle      VARCHAR(200) NOT NULL,
    rang         INTEGER      NOT NULL,
    UNIQUE (tc_id, code)
);

-- Structure d'un TCR : liste ordonnée d'attributs, positions calculées par cumul des tailles.
CREATE TABLE fmt_tcr_attribut (
    tcr_id       BIGINT  NOT NULL REFERENCES fmt_tcr (id) ON DELETE CASCADE,
    attribut_id  BIGINT  NOT NULL REFERENCES fmt_attribut (id),
    rang         INTEGER NOT NULL,
    PRIMARY KEY (tcr_id, rang)
);

-- Fichier : enveloppe commune à tous les enregistrements + TC d'en-tête et de fin.
CREATE TABLE fmt_fichier (
    id                     BIGSERIAL PRIMARY KEY,
    code                   VARCHAR(30)  NOT NULL UNIQUE,
    famille                VARCHAR(20)  NOT NULL,
    libelle                VARCHAR(200) NOT NULL,
    longueur_enregistrement INTEGER     NOT NULL,
    position_tc            INTEGER      NOT NULL,
    longueur_tc            INTEGER      NOT NULL,
    position_sequence      INTEGER      NOT NULL,
    longueur_sequence      INTEGER      NOT NULL,
    position_tcr           INTEGER      NOT NULL,
    longueur_tcr           INTEGER      NOT NULL,
    position_donnees       INTEGER      NOT NULL,  -- première position décrite par les attributs des TCR
    tc_entete              VARCHAR(10)  NOT NULL,
    tc_fin                 VARCHAR(10)  NOT NULL,
    repertoire_import      VARCHAR(500),
    repertoire_export      VARCHAR(500),
    repertoire_historique  VARCHAR(500)
);

CREATE TABLE fmt_sous_fichier (
    id           BIGSERIAL PRIMARY KEY,
    famille      VARCHAR(20)  NOT NULL,
    code         VARCHAR(30)  NOT NULL,
    libelle      VARCHAR(200) NOT NULL,
    description  VARCHAR(1000),
    tc_entete    VARCHAR(10)  NOT NULL,
    tc_fin       VARCHAR(10)  NOT NULL,
    UNIQUE (famille, code)
);

CREATE TABLE fmt_fichier_sous_fichier (
    fichier_id       BIGINT  NOT NULL REFERENCES fmt_fichier (id) ON DELETE CASCADE,
    sous_fichier_id  BIGINT  NOT NULL REFERENCES fmt_sous_fichier (id),
    rang             INTEGER NOT NULL,
    PRIMARY KEY (fichier_id, sous_fichier_id)
);

-- TC autorisés dans un sous-fichier.
CREATE TABLE fmt_sous_fichier_tc (
    sous_fichier_id  BIGINT  NOT NULL REFERENCES fmt_sous_fichier (id) ON DELETE CASCADE,
    tc_id            BIGINT  NOT NULL REFERENCES fmt_tc (id),
    rang             INTEGER NOT NULL,
    PRIMARY KEY (sous_fichier_id, tc_id)
);
