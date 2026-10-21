-- Valeur des billets par type de cassette des GAB (la ligne « DENOMINATION » du journal NCR
-- n'est pas fiable : elle affiche 5 10 20 50 quelle que soit la monnaie chargée).
INSERT INTO parametre_application (cle, valeur, description) VALUES
 ('ejournal.coupures', '10000,5000,2000,1000',
  'Valeur (XOF) des billets des cassettes de type 1, 2, 3 et 4 des GAB, séparées par des virgules');

-- Montant réellement présenté au client : billets présentés × valeur des coupures.
ALTER TABLE ej_operation ADD COLUMN montant_billets BIGINT;

UPDATE ej_operation o SET montant_billets =
       coalesce((s.billets_presentes->>0)::bigint, 0) * 10000 + coalesce((s.billets_presentes->>1)::bigint, 0) * 5000
     + coalesce((s.billets_presentes->>2)::bigint, 0) * 2000  + coalesce((s.billets_presentes->>3)::bigint, 0) * 1000
FROM ej_session s
WHERE s.id = o.session_id AND o.type = 'RETRAIT' AND o.code_reponse = '00' AND s.billets_presentes IS NOT NULL
  AND (SELECT count(*) FROM ej_operation x WHERE x.session_id = s.id AND x.type = 'RETRAIT' AND x.code_reponse = '00') = 1;
