-- Tableau de bord de la direction : indicateurs du mois comparés au mois précédent.
INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('REPORTING_DIRECTION', 'Tableau de bord direction', 'REPORTING', 0);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'REPORTING_DIRECTION')
ON CONFLICT DO NOTHING;
