# Produit la correspondance VALIDÉE (database/data/communes/) à partir de la proposition relue
# et des décisions de relecture du 15/10/2026. Usage, depuis ekotrip-backend/ :
#   python3 docs/plans/communes/appliquer_decisions.py
import csv, json

SRC = 'docs/plans/communes/COMMUNES_CORRESPONDANCE_PROPOSITION.csv'
OUT = 'database/data/communes/'

# Référentiel officiel : geo.api.gouv.fr (COG INSEE + codes postaux La Poste), département 972, relevé le 15/10/2026.
OFFICIELLES = json.loads('''[{"nom":"L'Ajoupa-Bouillon","code":"97201","codesPostaux":["97216"]},{"nom":"Les Anses-d'Arlet","code":"97202","codesPostaux":["97217"]},{"nom":"Basse-Pointe","code":"97203","codesPostaux":["97218"]},{"nom":"Le Carbet","code":"97204","codesPostaux":["97221"]},{"nom":"Case-Pilote","code":"97205","codesPostaux":["97222"]},{"nom":"Le Diamant","code":"97206","codesPostaux":["97223"]},{"nom":"Ducos","code":"97207","codesPostaux":["97224"]},{"nom":"Fonds-Saint-Denis","code":"97208","codesPostaux":["97250"]},{"nom":"Fort-de-France","code":"97209","codesPostaux":["97200","97234"]},{"nom":"Le François","code":"97210","codesPostaux":["97240"]},{"nom":"Grand'Rivière","code":"97211","codesPostaux":["97218"]},{"nom":"Gros-Morne","code":"97212","codesPostaux":["97213"]},{"nom":"Le Lamentin","code":"97213","codesPostaux":["97232"]},{"nom":"Le Lorrain","code":"97214","codesPostaux":["97214"]},{"nom":"Macouba","code":"97215","codesPostaux":["97218"]},{"nom":"Le Marigot","code":"97216","codesPostaux":["97225"]},{"nom":"Le Marin","code":"97217","codesPostaux":["97290"]},{"nom":"Le Morne-Rouge","code":"97218","codesPostaux":["97260"]},{"nom":"Le Prêcheur","code":"97219","codesPostaux":["97250"]},{"nom":"Rivière-Pilote","code":"97220","codesPostaux":["97211"]},{"nom":"Rivière-Salée","code":"97221","codesPostaux":["97215"]},{"nom":"Le Robert","code":"97222","codesPostaux":["97231"]},{"nom":"Saint-Esprit","code":"97223","codesPostaux":["97270"]},{"nom":"Saint-Joseph","code":"97224","codesPostaux":["97212"]},{"nom":"Saint-Pierre","code":"97225","codesPostaux":["97250"]},{"nom":"Sainte-Anne","code":"97226","codesPostaux":["97227"]},{"nom":"Sainte-Luce","code":"97227","codesPostaux":["97228"]},{"nom":"Sainte-Marie","code":"97228","codesPostaux":["97230"]},{"nom":"Schœlcher","code":"97229","codesPostaux":["97233"]},{"nom":"La Trinité","code":"97230","codesPostaux":["97220"]},{"nom":"Les Trois-Îlets","code":"97231","codesPostaux":["97229"]},{"nom":"Le Vauclin","code":"97232","codesPostaux":["97280"]},{"nom":"Le Morne-Vert","code":"97233","codesPostaux":["97226"]},{"nom":"Bellefontaine","code":"97234","codesPostaux":["97222"]}]''')
assert len(OFFICIELLES) == 34
CODE = {c['nom']: c['code'] for c in OFFICIELLES}

# Décisions de relecture (valeur brute → modifications)
DECISIONS = {
    "Anses-d'Arlet / Caravelle": dict(statut='PROVISOIRE', decision="ACT-064 : MULTI validé provisoirement, en attente de confirmation de la porteuse."),
    'Fort-de-France / Nord': dict(statut='VALIDE', decision='PAT-080 : commune + zone Nord validées.'),
    "Nord de l'île": dict(statut='VALIDE', type='COMMUNE_ZONE', communes=['Le Morne-Rouge'], zone='Nord',
                          decision="NAT-021 : Le Morne-Rouge rattaché (code postal 97260, accès route de l'Aileron) + zone Nord."),
    'Nord Caraïbe': dict(statut='VALIDE', decision='NAT-020 : zone seule, sans commune.'),
    'Centre / Sud': dict(statut='VALIDE', decision='ACT-007 : zones seules.'),
    'Martinique – activité mobile': dict(statut='VALIDE', decision='PRO-028 : zone seule (activité mobile).'),
    'Tartane (La Trinité)': dict(statut='VALIDE', decision='Validé.'),
    "Grande Anse d'Arlet (Les Anses-d'Arlet)": dict(statut='VALIDE', decision='Validé.'),
}

rows = list(csv.DictReader(open(SRC, encoding='utf-8-sig'), delimiter=';'))
out = []
for r in rows:
    d = DECISIONS.get(r['valeur_brute'], {})
    if r['a_valider'] == 'OUI' and not d:
        assert r['nb_fiches_actives'] == '0', f"Ligne active sans décision : {r['valeur_brute']}"
        d = dict(statut='VALIDE', decision='Fiches inactives uniquement : validé tel quel.')
    communes = d.get('communes', [c for c in r['communes_officielles'].split(' | ') if c])
    out.append({
        'valeur_brute': r['valeur_brute'],
        'type': d.get('type', r['type']),
        'codes_insee': ' | '.join(CODE[c] for c in communes),
        'communes_officielles': ' | '.join(communes),
        'lieu_dit': r['lieu_dit'],
        'zone': d.get('zone', r['zone']),
        'statut_validation': d.get('statut', 'VALIDE'),
        'decision': d.get('decision', ''),
        'commentaire': r['commentaire'],
    })

with open(OUT + 'communes_correspondance.csv', 'w', newline='', encoding='utf-8') as f:
    w = csv.DictWriter(f, list(out[0]), delimiter=';', lineterminator='\n'); w.writeheader(); w.writerows(out)
with open(OUT + 'communes_officielles.csv', 'w', newline='', encoding='utf-8') as f:
    w = csv.writer(f, delimiter=';', lineterminator='\n'); w.writerow(['code_insee', 'nom', 'codes_postaux'])
    for c in sorted(OFFICIELLES, key=lambda c: c['code']):
        w.writerow([c['code'], c['nom'], ' | '.join(c['codesPostaux'])])

print(len(out), 'correspondances ;', sum(o['statut_validation'] == 'PROVISOIRE' for o in out), 'provisoire(s)')
