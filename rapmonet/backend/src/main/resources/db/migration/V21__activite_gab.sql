-- Activité des GAB : tableau de bord par GAB et par jour, à partir des journaux NCR intégrés.
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('gab.activite.plage_debut', '07:00', 'Début de la plage horaire où l''inactivité d''un GAB est surveillée (HH:mm)'),
 ('gab.activite.plage_fin', '21:00', 'Fin de la plage horaire où l''inactivité d''un GAB est surveillée (HH:mm)'),
 ('gab.activite.inactivite_alerte_minutes', '180', 'Durée sans passage de carte, dans la plage horaire, qui signale un GAB'),
 ('gab.activite.taux_refus_alerte', '25', 'Taux de retraits refusés (%) qui signale un GAB'),
 ('gab.activite.tentatives_min', '10', 'Nombre minimal de retraits tentés dans la journée pour calculer le taux de refus')
ON CONFLICT (cle) DO NOTHING;

INSERT INTO securite_menu (code, libelle, parent_code, rang) VALUES
 ('GAB_ACTIVITE', 'Activité des GAB', 'GAB', 0);
INSERT INTO securite_profil_menu (profil_code, menu_code) VALUES ('ADMIN', 'GAB_ACTIVITE')
ON CONFLICT DO NOTHING;
