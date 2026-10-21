-- Lot 7 : suivi des chargebacks GIM (émission et acquisition).

-- Recherche des opérations d'un même cycle (présentation, chargeback, représentation) par ARN.
CREATE INDEX idx_lis_transaction_arn ON lis_transaction ((attributs->>'LIS_ACQUIRERS_REFERENCE_NUMBER'));

INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('chargeback.delai_jours', '45',
  'Délai (jours) de représentation d''un chargeback, compté depuis la journée LIS du chargeback');

-- Un dossier par ARN de chargeback ; le cycle de vie est relu dans les fichiers LIS intégrés.
CREATE TABLE chargeback_dossier (
    id                    BIGSERIAL    PRIMARY KEY,
    arn                   VARCHAR(23)  NOT NULL UNIQUE,
    sens                  VARCHAR(12)  NOT NULL,           -- EMISSION (carte de la banque), ACQUISITION (carte d'une autre banque)
    tc                    VARCHAR(2)   NOT NULL,           -- 15 achat, 16 crédit, 17 retrait
    rrn                   VARCHAR(12),
    carte                 VARCHAR(25),                     -- masquée
    carte_empreinte       VARCHAR(64),
    terminal              VARCHAR(20),
    marchand              VARCHAR(50),
    date_transaction      DATE,
    heure_transaction     VARCHAR(6),
    autorisation          VARCHAR(6),
    montant               BIGINT,
    devise                VARCHAR(3),
    motif                 VARCHAR(4),
    reference_chargeback  VARCHAR(6),
    journee_chargeback    DATE         NOT NULL,
    regularisation        VARCHAR(5)   NOT NULL DEFAULT 'NON',   -- NON, OUI, REJET
    commentaire           VARCHAR(1000),
    regularise_par        VARCHAR(50),
    regularise_le         TIMESTAMPTZ,
    archive               BOOLEAN      NOT NULL DEFAULT false,
    archive_par           VARCHAR(50),
    archive_le            TIMESTAMPTZ,
    cree_le               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chargeback_dossier ON chargeback_dossier (sens, archive, journee_chargeback DESC);
CREATE INDEX idx_chargeback_rrn ON chargeback_dossier (rrn);

-- Menus : le menu Chargebacks devient un groupe.
INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('CHARGEBACK_EMISSION',    'Chargebacks émission',    'CHARGEBACK', 1),
 ('CHARGEBACK_ACQUISITION', 'Chargebacks acquisition', 'CHARGEBACK', 2);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'CHARGEBACK_EMISSION'), ('ADMIN', 'CHARGEBACK_ACQUISITION');
