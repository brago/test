import json, re, sys
S = {s['id']: s for s in json.load(open(sys.argv[1], encoding='utf-8'))}
out = open(sys.argv[2], 'w', encoding='utf-8')

LIBELLES = {
 '90': 'En-tête de fichier LIS', '91': 'Fin de fichier LIS',
 '92': 'En-tête sous-fichier commerçant (FC)', '93': 'Fin sous-fichier commerçant (FC)',
 '94': 'En-tête sous-fichier Visa (FV)', '95': 'Fin sous-fichier Visa (FV)',
 '96': 'En-tête sous-fichier Mastercard (FM)', '97': 'Fin sous-fichier Mastercard (FM)',
 '98': 'En-tête sous-fichier GIM local (FL)', '99': 'Fin sous-fichier GIM local (FL)',
 '80': 'En-tête sous-fichier switch (FS)', '81': 'Fin sous-fichier switch (FS)',
 '70': 'Crédit commerçant (domiciliation)', '71': 'Débit commerçant (domiciliation)', '78': 'TC 78 (format TC 70/71)',
 '72': 'Avis d''incident de paiement', '73': 'Avis de remise achat sur GAB', '74': 'Avis de remise cash advance',
 '75': 'Avis de remise retrait', '76': 'Avis de remise achat', '77': 'Avis de remise crédit voucher',
 '05': 'Achat (débit porteur)', '06': 'Crédit voucher (crédit porteur)', '07': 'Retrait (débit porteur)',
 '09': 'Ajustement débit retrait', '19': 'Ajustement crédit retrait',
 '15': 'Chargeback achat', '16': 'Chargeback crédit voucher', '17': 'Chargeback retrait',
 '25': 'Redressement achat', '26': 'Redressement crédit voucher', '27': 'Redressement retrait',
 '35': 'Redressement chargeback achat', '36': 'Redressement chargeback crédit voucher', '37': 'Redressement chargeback retrait',
 '10': 'Frais débit (fee collection)', '20': 'Frais crédit (funds disbursement)',
 '40': 'Avis de fraude', '46': 'Règlement', '48': 'Avis d''autorisation', '49': 'Avis d''acquisition de transaction',
 '50': 'Message texte', '51': 'Demande de copie originale', '52': 'Demande de copie', '53': 'Réponse à demande de copie',
 '03': 'TC 03', '62': 'TC 62 (délégation)', '64': 'TC 64 (émission)', '65': 'TC 65 (délégation)', '66': 'TC 66 (délégation)',
 '68': 'Demande de personnalisation de cartes', '82': 'Avis temps réel',
 # TC dont la structure n'a pas pu être extraite automatiquement
 '01': 'TC 01', '02': 'TC 02', '04': 'TC 04', '41': 'TC 41', '47': 'TC 47', '55': 'TC 55 (émission)',
 '56': 'Fichier quota de liste noire', '57': 'TC 57', '60': 'TC 60 (service complémentaire)', '61': 'TC 61 (délégation)',
 '63': 'TC 63 (émission)', '67': 'Short statement (délégation)',
}
FAMILLE_05 = ['05','06','07','15','16','17','25','26','27','35','36','37','09','19']
AVEC_TCR2 = ['05','06','15','16','25','26','35','36']
# (section, TC concernés, code TCR)
STRUCTURES = [
 ('7.1', ['90'], '0'), ('7.2', ['91'], '0'),
 ('7.3', ['92','94','96','98','80'], '0'), ('7.4', ['93','95','97','99','81'], '0'),
 ('7.7', ['03'], '0'), ('7.8', ['70','71','78'], '0'), ('7.9', ['70','71','78'], '1'),
 ('7.11', ['73','74','75','76','77'], '0'),
 ('7.12', FAMILLE_05, '0'), ('7.13', FAMILLE_05, '1'), ('7.14', AVEC_TCR2, '2'), ('7.15', FAMILLE_05, '3'),
 ('7.16', ['10','20'], '0'), ('7.17', ['10','20'], '1'),
 ('7.18', ['40'], '0'), ('7.20', ['48'], '0'),
 ('7.21', ['49'], '0'), ('7.22', ['49'], '1'), ('7.23', ['49'], '2'),
 ('7.24', ['50'], '0'), ('7.25', ['51','52','53'], '0'),
 ('7.39', ['62'], '0'), ('7.41', ['64'], '0'), ('7.42', ['65'], '0'), ('7.43', ['66'], '0'),
 ('7.45', ['68'], '0'), ('7.46', ['68'], '1'), ('7.47', ['82'], '0'), ('7.48', ['46'], '0'),
]
SANS_STRUCTURE = ['01','02','04','41','47','55','56','57','60','61','63','67','72']
SOUS_FICHIERS = [
 ('FC', 'Sous-fichier commerçant', 'Compensation et règlement des commerçants gérés par le GIM domiciliés à la banque.', '92', '93',
  ['70','71','72','73','74','75','76','77','46']),
 ('FV', 'Sous-fichier Visa', 'Transactions des porteurs de la banque chez des commerçants à l''étranger (système Visa).', '94', '95',
  ['05','06','07','15','16','17','25','26','27','35','36','37','10','20','40','48','50','51','52','53','46']),
 ('FM', 'Sous-fichier Mastercard', 'Transactions des porteurs de la banque chez des commerçants à l''étranger (système Mastercard).', '96', '97',
  ['05','06','07','15','16','17','25','26','27','35','36','37','10','20','50','51','52','53','46']),
 ('FL', 'Sous-fichier GIM local', 'Transactions des porteurs de la banque chez les commerçants, GAB et agences des banques locales (système GIM).', '98', '99',
  ['05','06','07','09','19','15','16','17','25','26','27','35','36','37','10','20','49','50','51','52','53','46']),
 ('FS', 'Sous-fichier switch', 'Image des messages en ligne échangés avec le serveur GIM ; utilisé pour la réconciliation.', '80', '81',
  ['82','60','61','62','63','64','65','66','67','68','55','56']),
]
CODES_SPECIAUX = {('7.1','005'): 'LIS_DATE_JOURNEE', ('7.2','004'): 'NB_ENREGISTREMENTS', ('7.4','004'): 'NB_ENREGISTREMENTS_SOUS_FICHIER'}
NUMERIQUES = re.compile(r'AMOUNT|COUNT|TOTAL|NUMBER OF|FEES?\b|RATE|EXPONENT|MSC|REVENUE|REJECTED|REIMBURSEMENT')

def q(s): return "'" + s.replace("'", "''") + "'" if s is not None else 'NULL'
def slug(nom):
    s = re.sub(r'[^A-Z0-9]+', '_', nom.upper().replace('’', '')).strip('_')
    return s[:48].rstrip('_')

dictionnaire = {}   # code -> (libelle, taille, pad, dec, type, specifique, confidentiel, description)
def type_de(f):
    nom, com = f['nom'].upper(), f['com'].upper()
    if f['type'] == 'N' and f['lg'] == 6 and ('DATE' in nom or 'JJMMAA' in com) and 'AAMMJJ' not in com:
        return 'DATE_JJMMAA', 0
    if f['type'] == 'N' and NUMERIQUES.search(nom) and 'EXPIRY' not in nom:
        # « 9(n)V99 » dans la spec, mais le GIM envoie le XOF sans décimales (constaté, voir migration V26).
        return 'NUMERIQUE', 0
    return 'ALPHA', 0

structures = {}  # (tc, tcr) -> [codes]
for sec, tcs, tcr in STRUCTURES:
    s = S[sec]
    assert not s['erreurs'] and s['fin'] == 256, sec
    codes = []; vus = {}
    for f in s['champs']:
        if f['pos'] < 10: continue
        nom = f['nom'].strip()
        if nom.upper().startswith('FILLER'):
            code = f"FILLER_{f['lg']}"; typ, dec = 'ALPHA', 0; spec = False
        else:
            typ, dec = type_de(f); spec = True
            code = CODES_SPECIAUX.get((sec, f['n'])) or 'LIS_' + slug(nom)
            k = code
            if code in dictionnaire and (dictionnaire[code][1], dictionnaire[code][4]) != (f['lg'], typ):
                code = f"{k}_{f['lg']}"
            n = vus.get(code, 0) + 1; vus[code] = n
            if n > 1: code = f"{code}_{n}"
        conf = ('CARD NUMBER' in nom.upper() and 'EXPIRY' not in nom.upper() and 'CAPTURE' not in nom.upper()
                and 'SEQUENCE' not in nom.upper() and f['lg'] >= 16)
        desc = (f['com'] + f" [LIS 3.038 §{sec} F.{f['n']}]").strip()[:1000]
        if code not in dictionnaire:
            dictionnaire[code] = (nom[:200], f['lg'], 'ZERO_GAUCHE' if f['type'] == 'N' else 'ESPACE_DROITE',
                                  dec, typ, spec, conf, desc)
        else:
            assert dictionnaire[code][1] == f['lg'], (code, sec)
        codes.append(code)
    for tc in tcs:
        structures[(tc, tcr)] = codes

tous_tc = sorted({tc for tc, _ in structures} | set(SANS_STRUCTURE), key=lambda c: (c not in ('90','91'), c))
tcrs = {}
for tc, tcr in structures: tcrs.setdefault(tc, set()).add(tcr)
for tc in SANS_STRUCTURE: tcrs.setdefault(tc, {'0'})

w = out.write
w("""-- Paramétrage du fichier LIS du GIM, généré à partir de la spécification « LIS 3.038 » (GIM/HPS),
-- Annexe B « Spécifications enregistrement LIS ». Ne pas modifier à la main : corriger les données
-- par une nouvelle migration ou par les écrans de paramétrage.
--
-- Non repris (structure à compléter) : TC 01, 02, 04, 41, 47, 55, 56, 57, 60, 61, 63, 67, 72
-- et les variantes Visa du TC 46 (§7.50 à §7.59, sous-fichier FV).

INSERT INTO fmt_fichier (code, famille, libelle, longueur_enregistrement,
                         position_tc, longueur_tc, position_sequence, longueur_sequence,
                         position_tcr, longueur_tcr, position_donnees, tc_entete, tc_fin,
                         repertoire_import, repertoire_export, repertoire_historique)
VALUES ('LIS_GIM', 'LIS', 'Fichier LIS du GIM (Local Interchange System)', 256,
        1, 2, 3, 6, 9, 1, 10, '90', '91', 'LIS/CURRENT', 'LIS/EXPORT', 'LIS/HISTORIQUE');

INSERT INTO fmt_sous_fichier (famille, code, libelle, description, tc_entete, tc_fin) VALUES
""")
w(',\n'.join(f" ('LIS', {q(c)}, {q(l)}, {q(d)}, {q(e)}, {q(f)})" for c, l, d, e, f, _ in SOUS_FICHIERS) + ';\n\n')
w("""INSERT INTO fmt_fichier_sous_fichier (fichier_id, sous_fichier_id, rang)
SELECT f.id, s.id, r.rang FROM fmt_fichier f
JOIN (VALUES ('FC', 1), ('FV', 2), ('FM', 3), ('FL', 4), ('FS', 5)) AS r(code, rang) ON TRUE
JOIN fmt_sous_fichier s ON s.famille = 'LIS' AND s.code = r.code
WHERE f.code = 'LIS_GIM';

INSERT INTO fmt_tc (famille, code, libelle, description, longueur, nb_tcr) VALUES
""")
w(',\n'.join(f" ('LIS', {q(tc)}, {q(LIBELLES[tc])}, {q(None if tc not in SANS_STRUCTURE else 'Structure non encore paramétrée.')}, 256, {len(tcrs[tc])})" for tc in tous_tc) + ';\n\n')
w("INSERT INTO fmt_tcr (tc_id, code, libelle, rang)\nSELECT tc.id, r.tcr, 'TCR ' || r.tcr, r.tcr::int\nFROM (VALUES\n")
w(',\n'.join(f" ({q(tc)}, {q(t)})" for tc in tous_tc for t in sorted(tcrs[tc])) + "\n) AS r(tc, tcr)\nJOIN fmt_tc tc ON tc.famille = 'LIS' AND tc.code = r.tc;\n\n")
w("INSERT INTO fmt_sous_fichier_tc (sous_fichier_id, tc_id, rang)\nSELECT s.id, tc.id, r.rang\nFROM (VALUES\n")
w(',\n'.join(f" ({q(sf)}, {q(tc)}, {i+1})" for sf, _, _, _, _, l in SOUS_FICHIERS for i, tc in enumerate(l)) +
  "\n) AS r(sf, tc, rang)\nJOIN fmt_sous_fichier s ON s.famille = 'LIS' AND s.code = r.sf\nJOIN fmt_tc tc ON tc.famille = 'LIS' AND tc.code = r.tc;\n\n")
w("INSERT INTO fmt_attribut (code, libelle, taille, pad, decimales, type_donnee, specifique, confidentiel, description) VALUES\n")
w(',\n'.join(f" ({q(c)}, {q(v[0])}, {v[1]}, {q(v[2])}, {v[3]}, {q(v[4])}, {str(v[5]).upper()}, {str(v[6]).upper()}, {q(v[7])})"
              for c, v in dictionnaire.items()) + ';\n\n')
w("INSERT INTO fmt_tcr_attribut (tcr_id, attribut_id, rang)\nSELECT tcr.id, a.id, r.rang\nFROM (VALUES\n")
w(',\n'.join(f" ({q(tc)}, {q(t)}, {q(c)}, {i+1})" for (tc, t), codes in sorted(structures.items()) for i, c in enumerate(codes)) +
  "\n) AS r(tc, tcr, attribut, rang)\nJOIN fmt_tc tc ON tc.famille = 'LIS' AND tc.code = r.tc\nJOIN fmt_tcr tcr ON tcr.tc_id = tc.id AND tcr.code = r.tcr\nJOIN fmt_attribut a ON a.code = r.attribut;\n")
print(f"TC={len(tous_tc)} structures={len(structures)} attributs={len(dictionnaire)} confidentiels={[c for c,v in dictionnaire.items() if v[6]]}")
