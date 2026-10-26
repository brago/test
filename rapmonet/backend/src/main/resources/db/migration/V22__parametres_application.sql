-- Paramètres de l'application modifiables à l'écran, avec historique motivé.
ALTER TABLE parametre_application ADD COLUMN modifie_par VARCHAR(50);

CREATE TABLE parametre_historique (
    id           BIGSERIAL     PRIMARY KEY,
    cle          VARCHAR(100)  NOT NULL,
    avant        VARCHAR(1000),
    apres        VARCHAR(1000) NOT NULL,
    motif        VARCHAR(500)  NOT NULL,
    modifie_par  VARCHAR(50),
    modifie_le   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_parametre_historique ON parametre_historique (id DESC);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('PARAMETRAGE_APPLICATION', 'Paramètres de l''application', 'PARAMETRAGE', 9);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'PARAMETRAGE_APPLICATION')
ON CONFLICT DO NOTHING;
