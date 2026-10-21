-- Socle : paramètres généraux de l'application (clé / valeur).
CREATE TABLE parametre_application (
    cle          VARCHAR(100) PRIMARY KEY,
    valeur       VARCHAR(1000) NOT NULL,
    description  VARCHAR(500),
    modifie_le   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO parametre_application (cle, valeur, description) VALUES
    ('application.nom',   'RAPMONET', 'Nom affiché de l''application'),
    ('application.devise', 'XOF',     'Devise de référence');
