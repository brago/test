-- Lot 8 : indicateurs de performance et base des états réglementaires. Le menu Pilotage devient un groupe.
INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('REPORTING_KPI',           'Indicateurs (KPI)',             'REPORTING', 1),
 ('REPORTING_REGLEMENTAIRE', 'Réglementaire BCEAO / VISA',    'REPORTING', 2);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES
 ('ADMIN', 'REPORTING_KPI'), ('ADMIN', 'REPORTING_REGLEMENTAIRE'),
 ('CONSULTATION', 'REPORTING'), ('CONSULTATION', 'REPORTING_KPI');
