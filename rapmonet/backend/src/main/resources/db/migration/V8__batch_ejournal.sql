-- Traitement automatique (batch) des journaux GAB.

INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('ejournal.batch.actif', 'true', 'Intégration automatique des journaux GAB activée'),
 ('ejournal.batch.heure', '04:00', 'Heure quotidienne d''exécution (HH:mm, fuseau du serveur RAPMONET)'),
 ('ejournal.batch.repertoire_entree', 'donnees/ejournal/entree',
  'Dossier déposé par les GAB (un sous-dossier par GAB accepté). Chemin absolu ou relatif au dossier de lancement.'),
 ('ejournal.batch.repertoire_archive', 'donnees/ejournal/archive', 'Journaux intégrés (ou déjà intégrés), classés par GAB et par date'),
 ('ejournal.batch.repertoire_rejet', 'donnees/ejournal/rejet', 'Journaux refusés, avec un fichier .erreur.txt expliquant le motif');

-- Historique des exécutions des traitements automatiques.
CREATE TABLE batch_execution (
    id                BIGSERIAL    PRIMARY KEY,
    traitement        VARCHAR(30)  NOT NULL,           -- EJOURNAL
    declencheur       VARCHAR(20)  NOT NULL,           -- PLANIFIE, MANUEL
    lance_par         VARCHAR(50),
    debut             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fin               TIMESTAMPTZ,
    statut            VARCHAR(20)  NOT NULL,           -- EN_COURS, TERMINE, TERMINE_AVEC_REJETS, ERREUR, INTERROMPU
    nb_fichiers       INTEGER      NOT NULL DEFAULT 0,
    nb_integres       INTEGER      NOT NULL DEFAULT 0,
    nb_deja_integres  INTEGER      NOT NULL DEFAULT 0,
    nb_rejetes        INTEGER      NOT NULL DEFAULT 0,
    nb_operations     INTEGER      NOT NULL DEFAULT 0,
    message           VARCHAR(2000),
    detail            JSONB        NOT NULL DEFAULT '[]'::jsonb   -- un élément par fichier traité
);
CREATE INDEX idx_batch_execution ON batch_execution (traitement, debut DESC);
-- Verrou : une seule exécution en cours par traitement, même avec plusieurs serveurs.
CREATE UNIQUE INDEX uq_batch_execution_en_cours ON batch_execution (traitement) WHERE statut = 'EN_COURS';
