-- Journaux électroniques des GAB (E-journaux NCR).

INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('ejournal.ncr.format_date', 'yy/MM/dd',
  'Format de la date des tickets des journaux NCR (yy/MM/dd : « 26/05/22 » = 22/05/2026). Syntaxe java.time.');

-- Codes réponse (paramétrage « Codes réponses » de MONETICA V2), valeurs usuelles ISO 8583.
CREATE TABLE code_reponse (
    code     VARCHAR(4)   PRIMARY KEY,
    libelle  VARCHAR(200) NOT NULL
);
INSERT INTO code_reponse (code, libelle) VALUES
 ('00', 'Approuvée'),
 ('01', 'Appeler l''émetteur'),
 ('03', 'Commerçant invalide'),
 ('04', 'Capturer la carte'),
 ('05', 'Refusée'),
 ('12', 'Transaction invalide'),
 ('13', 'Montant invalide'),
 ('14', 'Numéro de carte invalide'),
 ('30', 'Erreur de format'),
 ('41', 'Carte perdue'),
 ('43', 'Carte volée'),
 ('51', 'Provision insuffisante'),
 ('54', 'Carte expirée'),
 ('55', 'Code PIN erroné'),
 ('57', 'Transaction non permise au porteur'),
 ('58', 'Transaction non permise au terminal'),
 ('61', 'Plafond de retrait dépassé'),
 ('62', 'Carte restreinte'),
 ('65', 'Nombre de retraits dépassé'),
 ('68', 'Réponse reçue trop tard'),
 ('75', 'Nombre d''essais du code PIN dépassé'),
 ('91', 'Émetteur indisponible'),
 ('94', 'Transaction en double'),
 ('96', 'Mauvais fonctionnement du système');

CREATE TABLE ej_fichier (
    id                     BIGSERIAL    PRIMARY KEY,
    nom                    VARCHAR(255) NOT NULL,
    empreinte_sha256       CHAR(64)     NOT NULL UNIQUE,
    terminal_id            VARCHAR(20)  NOT NULL,
    date_debut             DATE,
    date_fin               DATE,
    nb_sessions            INTEGER      NOT NULL,
    nb_operations          INTEGER      NOT NULL,   -- opérations enregistrées (hors doublons)
    nb_doublons            INTEGER      NOT NULL,   -- opérations déjà présentes (journaux qui se chevauchent)
    nb_retraits_approuves  INTEGER      NOT NULL,
    montant_retraits       BIGINT       NOT NULL,
    nb_evenements          INTEGER      NOT NULL,
    nb_avertissements      INTEGER      NOT NULL,
    integre_le             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    integre_par            VARCHAR(50)
);
CREATE INDEX idx_ej_fichier_terminal ON ej_fichier (terminal_id, date_debut);

CREATE TABLE ej_anomalie (
    id          BIGSERIAL     PRIMARY KEY,
    fichier_id  BIGINT        NOT NULL REFERENCES ej_fichier (id) ON DELETE CASCADE,
    ligne       INTEGER,
    gravite     VARCHAR(20)   NOT NULL,
    message     VARCHAR(1000) NOT NULL
);

-- Session client : de « TRANSACTION START » à « TRANSACTION END » ; texte = extrait du journal.
CREATE TABLE ej_session (
    id                 BIGSERIAL    PRIMARY KEY,
    fichier_id         BIGINT       NOT NULL REFERENCES ej_fichier (id) ON DELETE CASCADE,
    rang               INTEGER      NOT NULL,
    numero_ligne       INTEGER      NOT NULL,
    terminal_id        VARCHAR(20)  NOT NULL,
    date_session       DATE,                     -- date des tickets, ou des sessions voisines si aucun ticket
    heure_debut        TIME,
    carte              VARCHAR(25),              -- telle qu'imprimée (masquée)
    nb_operations      INTEGER      NOT NULL,
    billets_presentes  JSONB,                    -- [23, 12, 5, 0] par type de cassette
    billets_pris       BOOLEAN      NOT NULL,
    compteurs          JSONB,                    -- bloc CASH TOTAL
    incidents          JSONB        NOT NULL,
    complete           BOOLEAN      NOT NULL,
    texte              TEXT         NOT NULL
);
CREATE INDEX idx_ej_session_terminal ON ej_session (terminal_id, date_session);

CREATE TABLE ej_operation (
    id            BIGSERIAL    PRIMARY KEY,
    fichier_id    BIGINT       NOT NULL REFERENCES ej_fichier (id) ON DELETE CASCADE,
    session_id    BIGINT       NOT NULL REFERENCES ej_session (id) ON DELETE CASCADE,
    terminal_id   VARCHAR(20)  NOT NULL,
    type          VARCHAR(10)  NOT NULL,         -- RETRAIT, SOLDE
    statut        VARCHAR(20)  NOT NULL,         -- DISTRIBUE, APPROUVE, NON_PRIS, REFUSE, INCONNU
    date_heure    TIMESTAMP,
    carte         VARCHAR(25),
    reference     VARCHAR(20),
    montant       BIGINT,
    devise        VARCHAR(3),
    code_reponse  VARCHAR(4),
    compte        VARCHAR(30),
    numero_ligne  INTEGER      NOT NULL,
    -- Une même opération présente dans deux journaux qui se chevauchent n'est enregistrée qu'une fois.
    cle_unicite   VARCHAR(300) NOT NULL UNIQUE
);
CREATE INDEX idx_ej_operation_date ON ej_operation (date_heure);
CREATE INDEX idx_ej_operation_terminal ON ej_operation (terminal_id, date_heure);

-- Passages en mode superviseur : arrêté des compteurs, ajout d'espèces…
CREATE TABLE ej_evenement (
    id                BIGSERIAL    PRIMARY KEY,
    fichier_id        BIGINT       NOT NULL REFERENCES ej_fichier (id) ON DELETE CASCADE,
    terminal_id       VARCHAR(20)  NOT NULL,
    date_heure        TIMESTAMP,
    actions           JSONB        NOT NULL,
    releves           JSONB        NOT NULL,
    especes_ajoutees  JSONB        NOT NULL,
    numero_ligne      INTEGER      NOT NULL,
    texte             TEXT         NOT NULL
);
CREATE INDEX idx_ej_evenement_terminal ON ej_evenement (terminal_id, date_heure);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('CHARGEMENT_EJOURNAL',  'Journaux GAB',  'CHARGEMENT',  3),
 ('EXPLORATION_EJOURNAL', 'Journaux GAB',  'EXPLORATION', 3);

INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'CHARGEMENT_EJOURNAL'), ('ADMIN', 'EXPLORATION_EJOURNAL'),
 ('CONSULTATION', 'EXPLORATION_EJOURNAL');
