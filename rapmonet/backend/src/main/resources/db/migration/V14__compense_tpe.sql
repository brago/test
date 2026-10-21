-- Compense TPE : paiement des commerçants pour les achats acquis (LIS, TC 49), commissions et TAF.
-- Reprend la macro Excel « TRAITEMENT COMPENSE TPE ».

-- Comptes généraux et taux (les numéros de compte de la banque sont saisis à l'écran, jamais versionnés).
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('compense.compte.emis_onus',          '', 'Compte à débiter : achats GIM des cartes de la banque (EMIS ONUS)'),
 ('compense.compte.acq_gim',            '', 'Compte à débiter : achats GIM des cartes d''autres banques (ACQ GIM)'),
 ('compense.compte.acq_visa',           '', 'Compte à débiter : achats VISA (ACQ VISA)'),
 ('compense.compte.acq_mci',            '', 'Compte à débiter : achats Mastercard (ACQ MCI)'),
 ('compense.compte.compensation_visamci','', 'Compte de compensation VISA / Mastercard (CMPNSE VISAMCI)'),
 ('compense.compte.compensation_gim',   '', 'Compte de compensation GIM (CMPNSE GIM)'),
 ('compense.compte.commission',         '', 'Compte de commission acquéreur, part HT (CPTE COMM)'),
 ('compense.compte.taf',                '', 'Compte de la taxe sur les activités financières (CPTE TAF)'),
 ('compense.compte.commission_gim',     '', 'Compte de commission GIM (COMM GIM) – non utilisé par la macro'),
 ('compense.taux_taf',                  '0.17', 'Taux de TAF : commission HT = CCI / (1 + taux)'),
 ('compense.code_operation',            '91000', 'Code opération (CODOPSC) des écritures de la macro comptable'),
 ('compense.devise',                    'XOF', 'Devise des écritures');

-- Référentiel des commerçants (feuille « N° COMPTES » de la macro).
CREATE TABLE commercant (
    terminal_id   VARCHAR(15)   PRIMARY KEY,         -- 8 chiffres, complété à gauche par des zéros
    nom           VARCHAR(100)  NOT NULL,
    compte        VARCHAR(30)   NOT NULL,             -- compte du commerçant à créditer
    taux_gim      NUMERIC(7, 5) NOT NULL DEFAULT 0,   -- 0.015 = 1,5 %
    taux_visa     NUMERIC(7, 5) NOT NULL DEFAULT 0,
    taux_mci      NUMERIC(7, 5) NOT NULL DEFAULT 0,
    actif         BOOLEAN       NOT NULL DEFAULT true,
    modifie_le    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modifie_par   VARCHAR(50)
);

CREATE SEQUENCE compense_reference;
CREATE TABLE compense_traitement (
    id             BIGSERIAL    PRIMARY KEY,
    reference      VARCHAR(20)  NOT NULL UNIQUE,
    journee        DATE         NOT NULL,             -- journée des fichiers LIS compensés
    parametres     JSONB        NOT NULL,             -- comptes et taux appliqués (figés à la création)
    nb_transactions INTEGER     NOT NULL,
    montant        BIGINT       NOT NULL,
    commission     BIGINT       NOT NULL,
    taf            BIGINT       NOT NULL,
    statut         VARCHAR(10)  NOT NULL,             -- SAISI, VALIDE, ANNULE
    cree_par       VARCHAR(50)  NOT NULL,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decide_par     VARCHAR(50),
    decide_le      TIMESTAMPTZ,
    motif          VARCHAR(500)
);
CREATE INDEX idx_compense_traitement ON compense_traitement (journee DESC);

CREATE TABLE compense_ligne (
    id                 BIGSERIAL     PRIMARY KEY,
    traitement_id      BIGINT        NOT NULL REFERENCES compense_traitement (id) ON DELETE CASCADE,
    lis_transaction_id BIGINT        NOT NULL,         -- achat TC 49 compensé
    actif              BOOLEAN       NOT NULL DEFAULT true,   -- faux quand le traitement est annulé
    reseau             VARCHAR(12)   NOT NULL,          -- GIM, VISA, MASTERCARD
    on_us              BOOLEAN       NOT NULL,          -- GIM sans interchange : carte de la banque
    terminal_id        VARCHAR(15)   NOT NULL,
    commercant         VARCHAR(100)  NOT NULL,
    compte_commercant  VARCHAR(30)   NOT NULL,
    compte_debit       VARCHAR(30)   NOT NULL,
    carte              VARCHAR(25),
    autorisation       VARCHAR(10),
    rrn                VARCHAR(12),
    arn                VARCHAR(23),
    date_transaction   DATE          NOT NULL,
    heure_transaction  VARCHAR(6),
    montant            BIGINT        NOT NULL,
    taux               NUMERIC(7, 5) NOT NULL,
    cci                NUMERIC(14, 4) NOT NULL,         -- commission TTC exacte (montant × taux)
    frais              BIGINT        NOT NULL,          -- CCI arrondie, prélevée au commerçant
    commission         BIGINT        NOT NULL,          -- part HT
    taf                BIGINT        NOT NULL
);
CREATE INDEX idx_compense_ligne ON compense_ligne (traitement_id);
-- Un achat n'est compensé qu'une fois (hors traitements annulés).
CREATE UNIQUE INDEX uq_compense_ligne_achat ON compense_ligne (lis_transaction_id) WHERE actif;

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('COMPENSE',              'Compense TPE',            NULL,        8);
UPDATE securite_menu SET rang = rang + 1 WHERE parent_code IS NULL AND code IN ('CHARGEBACK', 'REPORTING');
INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('COMPENSE_TRAITEMENT',   'Traitements de compense', 'COMPENSE', 1),
 ('COMPENSE_COMMERCANTS',  'Commerçants et comptes',  'COMPENSE', 2),
 ('COMPENSE_VALIDATION',   'Validation (4 yeux)',     'COMPENSE', 3);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'COMPENSE'), ('ADMIN', 'COMPENSE_TRAITEMENT'), ('ADMIN', 'COMPENSE_COMMERCANTS'), ('ADMIN', 'COMPENSE_VALIDATION');
