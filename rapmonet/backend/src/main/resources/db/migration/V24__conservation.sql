-- Conservation des données : durées par catégorie (conservation.CODE.mois, créées à la première modification),
-- purge manuelle ou planifiée chaque mois, historique des purges.
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('conservation.purge.planifiee', 'false', 'Purge automatique mensuelle des données dont la durée de conservation est dépassée'),
 ('conservation.purge.jour', '1', 'Jour du mois de la purge automatique (1 à 28), à 02:30')
ON CONFLICT (cle) DO NOTHING;

CREATE TABLE purge_execution (
    id          BIGSERIAL     PRIMARY KEY,
    declencheur VARCHAR(10)   NOT NULL,           -- MANUEL, PLANIFIE
    lance_par   VARCHAR(50),
    motif       VARCHAR(500),
    debut       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fin         TIMESTAMPTZ,
    statut      VARCHAR(12)   NOT NULL,           -- EN_COURS, TERMINE, ERREUR
    detail      JSONB         NOT NULL DEFAULT '[]' -- par catégorie : code, libellé, durée, limite, supprimés, erreur
);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('PARAMETRAGE_CONSERVATION', 'Conservation des données', 'PARAMETRAGE', 10);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'PARAMETRAGE_CONSERVATION')
ON CONFLICT DO NOTHING;
