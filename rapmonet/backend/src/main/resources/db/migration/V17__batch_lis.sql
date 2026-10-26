-- Intégration automatique des fichiers LIS déposés dans un dossier (même mécanique que les journaux GAB).
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('lis.batch.actif', 'true', 'Intégration automatique des fichiers LIS activée'),
 ('lis.batch.heure', '08:00', 'Heure quotidienne d''exécution (HH:mm, fuseau du serveur RAPMONET)'),
 ('lis.batch.repertoire_entree', 'donnees/lis/entree',
  'Dossier où sont déposés les fichiers LIS du GIM (SW et PR). Chemin absolu ou relatif au dossier de lancement.'),
 ('lis.batch.repertoire_archive', 'donnees/lis/archive', 'Fichiers LIS intégrés (ou déjà intégrés), classés par mois de journée'),
 ('lis.batch.repertoire_rejet', 'donnees/lis/rejet', 'Fichiers LIS refusés, avec un fichier .erreur.txt expliquant le motif');
