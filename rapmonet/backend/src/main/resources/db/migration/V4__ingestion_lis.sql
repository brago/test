-- Données LIS intégrées.

CREATE TABLE lis_fichier (
    id                  BIGSERIAL PRIMARY KEY,
    nom                 VARCHAR(255) NOT NULL,
    type_lis            VARCHAR(2)   NOT NULL CHECK (type_lis IN ('SW', 'PR')),
    format_code         VARCHAR(30)  NOT NULL,
    journee             DATE         NOT NULL,
    empreinte_sha256    CHAR(64)     NOT NULL UNIQUE,  -- empêche d'intégrer deux fois le même fichier
    entete              JSONB        NOT NULL,
    nb_enregistrements  INTEGER      NOT NULL,
    nb_transactions     INTEGER      NOT NULL,
    repartition_tc      JSONB        NOT NULL,         -- {"05": 69, "07": 616, …} en nombre de transactions
    nb_avertissements   INTEGER      NOT NULL,
    integre_le          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    integre_par         VARCHAR(100)
);
CREATE INDEX idx_lis_fichier_journee ON lis_fichier (journee, type_lis);

CREATE TABLE lis_anomalie (
    id          BIGSERIAL PRIMARY KEY,
    fichier_id  BIGINT       NOT NULL REFERENCES lis_fichier (id) ON DELETE CASCADE,
    ligne       INTEGER,
    gravite     VARCHAR(20)  NOT NULL,
    message     VARCHAR(1000) NOT NULL
);
CREATE INDEX idx_lis_anomalie_fichier ON lis_anomalie (fichier_id);

-- Une transaction = un TCR 0 et les TCR suivants du même TC.
CREATE TABLE lis_transaction (
    id            BIGSERIAL PRIMARY KEY,
    fichier_id    BIGINT       NOT NULL REFERENCES lis_fichier (id) ON DELETE CASCADE,
    numero        INTEGER      NOT NULL,           -- rang de la transaction dans le fichier
    sous_fichier  VARCHAR(30)  NOT NULL,
    tc            VARCHAR(10)  NOT NULL,
    sequence      INTEGER      NOT NULL,           -- séquence du TCR 0
    nb_tcr        INTEGER      NOT NULL,
    attributs     JSONB        NOT NULL,           -- attributs spécifiques décodés
    UNIQUE (fichier_id, numero)
);
CREATE INDEX idx_lis_transaction_tc ON lis_transaction (fichier_id, tc);

-- Lignes brutes, conservées telles que reçues (y compris en-têtes et fins).
CREATE TABLE lis_enregistrement (
    fichier_id          BIGINT       NOT NULL REFERENCES lis_fichier (id) ON DELETE CASCADE,
    sequence            INTEGER      NOT NULL,
    tc                  VARCHAR(10)  NOT NULL,
    tcr                 VARCHAR(10)  NOT NULL,
    transaction_numero  INTEGER,
    contenu             VARCHAR(4000) NOT NULL,
    PRIMARY KEY (fichier_id, sequence)
);
CREATE INDEX idx_lis_enregistrement_transaction ON lis_enregistrement (fichier_id, transaction_numero);
