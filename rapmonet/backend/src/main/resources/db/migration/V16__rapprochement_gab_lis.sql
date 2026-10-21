-- Rapprochement des retraits de cartes confrères sur les GAB de la banque : journaux NCR / avis d'acquisition LIS (TC 49, 6011).
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('rapprochement.gab_lis.tolerance_minutes', '120',
  'Écart maximal (minutes) entre l''heure du ticket GAB et l''heure de l''avis LIS pour rapprocher deux opérations'),
 ('rapprochement.gab_lis.delai_lis_jours', '3',
  'Jours laissés au GIM pour remonter un retrait dans les LIS avant de le déclarer non remonté');

-- Traitement des écarts (sans ligne : écart à traiter).
CREATE TABLE rapprochement_ecart (
    id           BIGSERIAL    PRIMARY KEY,
    cle          VARCHAR(320) NOT NULL UNIQUE,     -- EJ:<opération du journal> ou LIS:<fichier>:<numéro>
    type_ecart   VARCHAR(30)  NOT NULL,
    statut       VARCHAR(12)  NOT NULL,            -- JUSTIFIE, REGULARISE
    commentaire  VARCHAR(500),
    traite_par   VARCHAR(50),
    traite_le    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('RAPPROCHEMENT_GAB_LIS', 'GAB ↔ LIS (retraits confrères)', 'RAPPROCHEMENT', 1);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'RAPPROCHEMENT_GAB_LIS')
ON CONFLICT DO NOTHING;
