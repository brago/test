-- Montants LIS en XOF : la spécification LIS (3.038) annonce « 9(10)V99 » pour ces montants, mais le GIM les envoie
-- sans décimales (le XOF n'en a pas). Constaté sur des fichiers réels : un retrait TC 49 de 100 000 XOF était lu
-- 1 000,00 ; les TC 46, 75 et 76 et les frais d'interchange de même. On passe ces champs à 0 décimale et on corrige les
-- valeurs déjà intégrées (× 100). Un champ dont les décimales ont déjà été changées à l'écran n'est pas touché.
DO $$
DECLARE
  r RECORD;
  lisible TEXT;
BEGIN
  FOR r IN SELECT * FROM fmt_attribut WHERE decimales = 2 AND type_donnee = 'NUMERIQUE' AND code IN (
      'LIS_ACQUIRER_MSC_REVENUE',
      'LIS_AMOUNT_IN_LOCAL_CURRENCY_CURRENTLY_CFA',
      'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12',
      'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12_2',
      'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12_3',
      'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12_4',
      'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12_5',
      'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12_6',
      'LIS_CARDHOLDER_BILLING_AMOUNT',
      'LIS_INCOMING_AMOUNT_IN_SETTLEMENT_CURRENCY',
      'LIS_INTERCHANGE_FEES',
      'LIS_ISSUER_REIMBURSEMENT_FEE_IRF',
      'LIS_NET_AMOUNT_IN_CFA',
      'LIS_NET_AMOUNT_IN_SETTLEMENT_CURRENCY',
      'LIS_OUTGOING_AMOUNT_IN_SETTLEMENT_CURRENCY',
      'LIS_REPLACEMENT_AMOUNT_IN_TRANSACTION_CURRENCY',
      'LIS_SOURCE_OR_DESTINATION_AMOUNT_IN_LOCAL_CURRENCY_C',
      'LIS_TRANSACTION_AMOUNT',
      'LIS_TRANSACTION_AMOUNT_IN_TRANSACTION_CURRENCY')
  LOOP
    lisible := r.libelle || ' ; taille ' || r.taille || ' ; ' || r.type_donnee || '%s ; complément ' || r.pad
        || CASE WHEN r.specifique THEN ' ; stocké' ELSE ' ; non stocké' END
        || CASE WHEN r.confidentiel THEN ' ; confidentiel' ELSE '' END;
    UPDATE fmt_attribut SET decimales = 0 WHERE id = r.id;
    UPDATE lis_transaction
       SET attributs = jsonb_set(attributs, ARRAY[r.code], to_jsonb(round((attributs ->> r.code)::numeric * 100)))
     WHERE jsonb_typeof(attributs -> r.code) = 'number';
    INSERT INTO fmt_modification (format_code, objet, action, motif, avant, apres, modifie_par)
    VALUES ('LIS_GIM', 'Champ ' || r.code, 'CHAMP_MODIFIE',
            'Montant en XOF envoyé sans décimales par le GIM (constaté sur fichiers réels) ; valeurs déjà intégrées × 100.',
            format(lisible, ' (2 déc.)'), format(lisible, ''), 'migration V26');
  END LOOP;
END $$;
