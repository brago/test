-- Lot 5 : GAB – paramétrage, approvisionnements, arrêtés, validation à quatre yeux.

-- Profil GAB : cassettes A à D, coupure et type de billet NCR (colonne TYPE du bloc CASSETTE INFORMATION).
-- Les compteurs du journal sont donnés par type : plusieurs cassettes peuvent partager un type.
CREATE TABLE gab_profil (
    code        VARCHAR(10)  PRIMARY KEY,
    libelle     VARCHAR(100) NOT NULL,
    cassettes   JSONB        NOT NULL,   -- [{"cassette":"A","coupure":10000,"type":1}, …]
    modifie_le  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_par VARCHAR(50)
);
INSERT INTO gab_profil (code, libelle, cassettes) VALUES
 ('001', 'Standard 10 000 / 10 000 / 5 000 / 2 000',
  '[{"cassette":"A","coupure":10000,"type":1},{"cassette":"B","coupure":10000,"type":1},
    {"cassette":"C","coupure":5000,"type":2},{"cassette":"D","coupure":2000,"type":3}]');

CREATE TABLE gab (
    terminal_id      VARCHAR(20)  PRIMARY KEY,
    libelle          VARCHAR(100) NOT NULL,
    localisation     VARCHAR(200),
    agence           VARCHAR(20),
    marque           VARCHAR(20)  NOT NULL,          -- NCR, WINCOR, HYOSUNG…
    profil_code      VARCHAR(10)  NOT NULL REFERENCES gab_profil (code),
    compte_caisse    VARCHAR(30),
    compte_manquant  VARCHAR(30),
    compte_excedent  VARCHAR(30),
    actif            BOOLEAN      NOT NULL DEFAULT true,
    modifie_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_par      VARCHAR(50)
);

-- BIN des cartes de la banque (cartes « on-us »).
CREATE TABLE bin_carte (
    bin          VARCHAR(11)  PRIMARY KEY,
    libelle      VARCHAR(100) NOT NULL,
    type_carte   VARCHAR(10)  NOT NULL,             -- DEBIT, CREDIT, PREPAYEE
    modifie_le   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_par  VARCHAR(50)
);

CREATE SEQUENCE gab_appro_reference;
CREATE TABLE gab_appro (
    id             BIGSERIAL    PRIMARY KEY,
    reference      VARCHAR(20)  NOT NULL UNIQUE,
    terminal_id    VARCHAR(20)  NOT NULL REFERENCES gab (terminal_id),
    date_appro     TIMESTAMP    NOT NULL,
    gabiste        VARCHAR(100) NOT NULL,
    reference_externe VARCHAR(50),
    numero_bon     VARCHAR(50),
    commentaire    VARCHAR(1000),
    billetage      JSONB        NOT NULL,           -- [{"cassette":"A","type":1,"coupure":10000,"nombre":1000}, …]
    montant        BIGINT       NOT NULL,
    statut         VARCHAR(10)  NOT NULL,           -- SAISI, VALIDE, ANNULE
    saisi_par      VARCHAR(50)  NOT NULL,
    saisi_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_par    VARCHAR(50)  NOT NULL,
    modifie_le     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decide_par     VARCHAR(50),                     -- valideur ou auteur de l'annulation
    decide_le      TIMESTAMPTZ,
    motif          VARCHAR(500)
);
CREATE INDEX idx_gab_appro_terminal ON gab_appro (terminal_id, date_appro DESC);

CREATE SEQUENCE gab_arrete_reference;
CREATE TABLE gab_arrete (
    id                    BIGSERIAL    PRIMARY KEY,
    reference             VARCHAR(20)  NOT NULL UNIQUE,
    terminal_id           VARCHAR(20)  NOT NULL REFERENCES gab (terminal_id),
    appro_id              BIGINT       NOT NULL REFERENCES gab_appro (id),
    date_arrete           TIMESTAMP    NOT NULL,
    gabiste               VARCHAR(100) NOT NULL,
    commentaire           VARCHAR(1000),
    -- Par type de billet : compteurs du journal (1) cassettes et (2) rejets/retraits.
    journal               JSONB        NOT NULL,    -- [{"type":1,"coupure":10000,"cassette":134,"rejet":5}, …]
    -- Par cassette : inventaire physique (3) cassettes et (4) rejets.
    inventaire            JSONB        NOT NULL,    -- [{"cassette":"A","type":1,"coupure":10000,"nombre":67,"rejet":3}, …]
    transactions_attente  BIGINT       NOT NULL DEFAULT 0,   -- (7)
    solde_comptable       BIGINT,                            -- (8), saisi en attendant Amplitude
    montant_journal       BIGINT       NOT NULL,             -- (5) = (1) + (2)
    montant_physique      BIGINT       NOT NULL,             -- (6) = (3) + (4)
    retraits_journal      BIGINT,                            -- retraits distribués selon les journaux depuis l'appro
    statut                VARCHAR(10)  NOT NULL,
    saisi_par             VARCHAR(50)  NOT NULL,
    saisi_le              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_par           VARCHAR(50)  NOT NULL,
    modifie_le            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decide_par            VARCHAR(50),
    decide_le             TIMESTAMPTZ,
    motif                 VARCHAR(500)
);
CREATE INDEX idx_gab_arrete_terminal ON gab_arrete (terminal_id, date_arrete DESC);
-- Un appro n'est arrêté qu'une fois (hors arrêtés annulés).
CREATE UNIQUE INDEX uq_gab_arrete_appro ON gab_arrete (appro_id) WHERE statut <> 'ANNULE';

-- Menus : le menu GAB devient un groupe.
INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('PARAMETRAGE_GAB', 'GAB, profils et BIN',     'PARAMETRAGE', 3),
 ('GAB_APPRO',       'Approvisionnements',      'GAB',         1),
 ('GAB_ARRETE',      'Arrêtés',                 'GAB',         2),
 ('GAB_VALIDATION',  'Validation (4 yeux)',     'GAB',         3);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'PARAMETRAGE_GAB'), ('ADMIN', 'GAB_APPRO'), ('ADMIN', 'GAB_ARRETE'), ('ADMIN', 'GAB_VALIDATION');
