-- Règles du rapprochement GAB ↔ LIS paramétrables à l'écran, avec historique des modifications.
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('rapprochement.gab_lis.meme_carte', 'true',
  'Exiger la même carte (6 premiers et 4 derniers chiffres) pour rapprocher un ticket et un avis'),
 ('rapprochement.gab_lis.tolerance_montant', '0',
  'Écart de montant toléré (XOF) entre le ticket GAB et l''avis LIS'),
 ('rapprochement.gab_lis.reference_active', 'true',
  'Rapprocher aussi sur la référence du ticket, quelle que soit l''heure'),
 ('rapprochement.gab_lis.reference_champs', 'AUTORISATION,BORDEREAU,RRN',
  'Champs de l''avis LIS comparés à la référence du ticket'),
 ('rapprochement.gab_lis.reference_fenetre_jours', '1',
  'Écart maximal en jours entre ticket et avis pour un rapprochement sur la référence')
ON CONFLICT (cle) DO NOTHING;

CREATE TABLE rapprochement_regle_historique (
    id           BIGSERIAL     PRIMARY KEY,
    rapprochement VARCHAR(30)  NOT NULL,          -- GAB_LIS
    modifie_le   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modifie_par  VARCHAR(50),
    motif        VARCHAR(500)  NOT NULL,
    avant        TEXT          NOT NULL,          -- règles avant, lisibles
    apres        TEXT          NOT NULL
);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('RAPPROCHEMENT_REGLES', 'Règles de rapprochement', 'RAPPROCHEMENT', 2);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'RAPPROCHEMENT_REGLES')
ON CONFLICT DO NOTHING;
