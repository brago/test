-- Rapports VisaNet Settlement Service (fichier EP-747, nommé « VX » par le GIM : <BANQUE>_VX747[_n].txt).

-- Paramétrage : types de rapports connus. Seuls les rapports « integre » sont enregistrés
-- (équivalent des fichiers EP* cochés dans MONETICA V2). Un rapport inconnu est enregistré
-- avec un avertissement.
CREATE TABLE vss_type_rapport (
    code         VARCHAR(20)  PRIMARY KEY,
    libelle      VARCHAR(200) NOT NULL,
    description  VARCHAR(1000),
    integre      BOOLEAN      NOT NULL DEFAULT TRUE,
    rang         INTEGER      NOT NULL
);

INSERT INTO vss_type_rapport (code, libelle, description, rang) VALUES
 ('VSS-110',   'Synthèse du règlement',
  'Montant net à régler par SRE : interchange, frais de remboursement, frais Visa (devise de règlement).', 1),
 ('VSS-120',   'Valeur d''interchange',
  'Détail par type de transaction et table de taux : nombre, montant de compensation, valeur d''interchange.', 3),
 ('VSS-130',   'Frais de remboursement', 'Frais de remboursement (IRF) par programme et région.', 4),
 ('VSS-140',   'Frais Visa', 'Frais Visa (ISA, conversion de devises…) par type de transaction.', 5),
 ('VSS-210',   'Frais de conversion de devises', 'Montants et frais de conversion, en devise de règlement et de compensation.', 6),
 ('VSS-300',   'Récapitulatif financier SRE', 'Récapitulatif par SRE.', 7),
 ('VSS-900-S', 'Rapprochement synthétique',
  'Nombre et montant des transactions reçues de Visa et envoyées au règlement, par devise de compensation.', 8);

CREATE TABLE vss_fichier (
    id                    BIGSERIAL    PRIMARY KEY,
    nom                   VARCHAR(255) NOT NULL,
    empreinte_sha256      CHAR(64)     NOT NULL UNIQUE,
    date_traitement       DATE         NOT NULL,
    sre_id                VARCHAR(20),
    sre_nom               VARCHAR(200),
    nb_rapports           INTEGER      NOT NULL,
    nb_lignes             INTEGER      NOT NULL,
    -- Synthèse (VSS-110 et VSS-900-S) pour la liste des fichiers et le rapprochement LIS / VSS
    devise_reglement      VARCHAR(3),
    montant_net           NUMERIC(18, 2),   -- signé : négatif = débit de la banque
    nb_transactions       INTEGER,
    devise_compensation   VARCHAR(3),
    montant_compensation  NUMERIC(18, 2),   -- signé
    nb_avertissements     INTEGER      NOT NULL,
    integre_le            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    integre_par           VARCHAR(50)
);
CREATE INDEX idx_vss_fichier_date ON vss_fichier (date_traitement);

CREATE TABLE vss_anomalie (
    id          BIGSERIAL     PRIMARY KEY,
    fichier_id  BIGINT        NOT NULL REFERENCES vss_fichier (id) ON DELETE CASCADE,
    ligne       INTEGER,
    gravite     VARCHAR(20)   NOT NULL,
    message     VARCHAR(1000) NOT NULL
);

CREATE TABLE vss_rapport (
    id               BIGSERIAL    PRIMARY KEY,
    fichier_id       BIGINT       NOT NULL REFERENCES vss_fichier (id) ON DELETE CASCADE,
    rang             INTEGER      NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    titre            VARCHAR(200),
    sre_id           VARCHAR(20),
    sre_nom          VARCHAR(200),
    rollup           VARCHAR(200),
    entite_fonds     VARCHAR(200),
    date_traitement  DATE,
    date_rapport     DATE,
    nb_pages         INTEGER      NOT NULL,
    sans_donnees     BOOLEAN      NOT NULL,
    colonnes         JSONB        NOT NULL,   -- noms des colonnes, dans l'ordre
    UNIQUE (fichier_id, rang)
);
CREATE INDEX idx_vss_rapport_code ON vss_rapport (code, date_traitement);

CREATE TABLE vss_ligne (
    id                   BIGSERIAL     PRIMARY KEY,
    rapport_id           BIGINT        NOT NULL REFERENCES vss_rapport (id) ON DELETE CASCADE,
    rang                 INTEGER       NOT NULL,
    numero_ligne         INTEGER       NOT NULL,
    page                 INTEGER       NOT NULL,
    niveau               INTEGER       NOT NULL,
    chemin               VARCHAR(1000) NOT NULL,
    libelle              VARCHAR(200),
    devise_reglement     VARCHAR(3),
    devise_compensation  VARCHAR(4),
    valeurs              JSONB         NOT NULL   -- {"COUNT": {"nombre": 20}, "TOTAL AMOUNT": {"nombre": 2183.96, "sens": "DB"}}
);
CREATE INDEX idx_vss_ligne_rapport ON vss_ligne (rapport_id, rang);

-- Menus
INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('PARAMETRAGE_VSS', 'Rapports VSS',        'PARAMETRAGE', 2),
 ('CHARGEMENT_VSS',  'Fichiers VSS (VX)',   'CHARGEMENT',  2),
 ('EXPLORATION_VSS', 'Données VSS',         'EXPLORATION', 2);

INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'PARAMETRAGE_VSS'), ('ADMIN', 'CHARGEMENT_VSS'), ('ADMIN', 'EXPLORATION_VSS'),
 ('CONSULTATION', 'EXPLORATION_VSS');
