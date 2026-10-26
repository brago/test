-- Réclamations des porteurs : recherche transverse des opérations d'une carte / d'un compte, registre et suivi.
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('reclamation.delai_jours', '15', 'Délai de traitement d''une réclamation porteur (jours), à compter de son ouverture')
ON CONFLICT (cle) DO NOTHING;

CREATE SEQUENCE reclamation_numero_seq;

CREATE TABLE reclamation (
    id                BIGSERIAL     PRIMARY KEY,
    numero            VARCHAR(20)   NOT NULL UNIQUE,            -- REC-2026-00001
    carte             VARCHAR(25),                              -- masquée (6 premiers + 4 derniers chiffres)
    compte            VARCHAR(30),
    client            VARCHAR(200)  NOT NULL,
    contact           VARCHAR(200),
    canal             VARCHAR(20)   NOT NULL,                   -- GUICHET, TELEPHONE, COURRIER, EMAIL
    objet             VARCHAR(30)   NOT NULL,                   -- voir ReclamationsService.OBJETS
    description       VARCHAR(2000) NOT NULL,
    montant_conteste  BIGINT,
    date_operation    DATE,
    operations        JSONB         NOT NULL DEFAULT '[]',      -- opérations rattachées (instantané lisible)
    statut            VARCHAR(12)   NOT NULL,                   -- OUVERTE, EN_COURS, FONDEE, NON_FONDEE
    conclusion        VARCHAR(2000),
    echeance          DATE          NOT NULL,
    cree_par          VARCHAR(50),
    cree_le           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cloture_par       VARCHAR(50),
    cloture_le        TIMESTAMPTZ
);
CREATE INDEX idx_reclamation_statut ON reclamation (statut, echeance);
CREATE INDEX idx_reclamation_carte ON reclamation (carte);
CREATE INDEX idx_reclamation_compte ON reclamation (compte);

CREATE TABLE reclamation_suivi (
    id              BIGSERIAL     PRIMARY KEY,
    reclamation_id  BIGINT        NOT NULL REFERENCES reclamation (id) ON DELETE CASCADE,
    action          VARCHAR(20)   NOT NULL,                     -- OUVERTURE, PRISE_EN_CHARGE, COMMENTAIRE, CLOTURE, REOUVERTURE
    commentaire     VARCHAR(2000),
    auteur          VARCHAR(50),
    le              TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_reclamation_suivi ON reclamation_suivi (reclamation_id, id);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('RECLAMATIONS', 'Réclamations porteurs', NULL, 11),
 ('RECLAMATION_RECHERCHE', 'Recherche porteur', 'RECLAMATIONS', 1),
 ('RECLAMATION_SUIVI', 'Registre des réclamations', 'RECLAMATIONS', 2);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'RECLAMATIONS'), ('ADMIN', 'RECLAMATION_RECHERCHE'), ('ADMIN', 'RECLAMATION_SUIVI')
ON CONFLICT DO NOTHING;
