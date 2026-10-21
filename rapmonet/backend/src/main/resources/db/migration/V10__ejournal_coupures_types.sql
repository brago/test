-- Configuration des GAB confirmée : cassettes TOP et SECOND = type 1 (10 000), THIRD = type 2 (5 000),
-- FOURTH = type 3 (2 000). Le type 4 (billets de 1 000) n'est pas chargé.
-- Les nombres de billets du journal (NOTES PRESENTED, CASH TOTAL) sont donnés par type, pas par cassette.
UPDATE parametre_application
SET valeur = '10000,5000,2000,0',
    description = 'Valeur (XOF) des billets de type 1, 2, 3 et 4 (colonne TYPE du bloc CASSETTE INFORMATION ; '
                  || 'plusieurs cassettes peuvent avoir le même type). 0 = type non chargé.',
    modifie_le = now()
WHERE cle = 'ejournal.coupures';
