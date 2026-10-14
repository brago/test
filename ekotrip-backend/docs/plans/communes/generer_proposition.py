# Génère la proposition de correspondance communes (relecture humaine avant application).
# Usage, depuis ekotrip-backend/ : python3 docs/plans/communes/generer_proposition.py
import openpyxl, collections, csv, re, unicodedata, sys
OUT='docs/plans/communes/'
# Référentiel INSEE — COG au 1er janvier 2026 (insee.fr/fr/metadonnees/geographie/departement/972-martinique)
OFF=[('97201',"L'Ajoupa-Bouillon"),('97202',"Les Anses-d'Arlet"),('97203','Basse-Pointe'),('97234','Bellefontaine'),
('97204','Le Carbet'),('97205','Case-Pilote'),('97206','Le Diamant'),('97207','Ducos'),('97208','Fonds-Saint-Denis'),
('97209','Fort-de-France'),('97210','Le François'),('97211',"Grand'Rivière"),('97212','Gros-Morne'),('97213','Le Lamentin'),
('97214','Le Lorrain'),('97215','Macouba'),('97216','Le Marigot'),('97217','Le Marin'),('97218','Le Morne-Rouge'),
('97233','Le Morne-Vert'),('97219','Le Prêcheur'),('97220','Rivière-Pilote'),('97221','Rivière-Salée'),('97222','Le Robert'),
('97223','Saint-Esprit'),('97224','Saint-Joseph'),('97225','Saint-Pierre'),('97226','Sainte-Anne'),('97227','Sainte-Luce'),
('97228','Sainte-Marie'),('97229','Schœlcher'),('97230','La Trinité'),('97231','Les Trois-Îlets'),('97232','Le Vauclin')]
assert len(OFF)==34 and len({c for c,_ in OFF})==34
def key(s):
    s=s.replace('’',"'").replace('œ','oe').replace('Œ','OE')
    s=unicodedata.normalize('NFKD',s).encode('ascii','ignore').decode().lower()
    s=re.sub(r"^(les|le|la|l')\s*",'',s.strip())
    return re.sub(r'[^a-z]','',s)
OFFK={key(n):(c,n) for c,n in OFF}

# Cas tranchés à la main (zone, lieu-dit, absence de commune) : toujours « à valider ».
MANUAL={
 '(vide)': ([], '', '', 'Valeur vide (fiche inactive) : aucune commune déduite.'),
 'Centre / Sud': ([], '', 'Centre | Sud', 'Pratique multi-sites (yole) : zones seulement, aucune commune.'),
 'Côte Nord-Caraïbe': ([], '', 'Nord Caraïbe', 'Zone seulement (fiche inactive).'),
 'Nord Caraïbe': ([], '', 'Nord Caraïbe', 'Zone seulement. Code postal 97226 (Le Morne-Vert) : faut-il aussi rattacher cette commune ?'),
 "Nord de l'île": ([], '', 'Nord', 'Zone seulement. Code postal 97260 (Le Morne-Rouge) : faut-il aussi rattacher cette commune ?'),
 'Sud Caraïbe': ([], '', 'Sud Caraïbe', 'Zone seulement (fiche inactive).'),
 'Île entière': ([], '', 'Île entière', 'Toute la Martinique : aucune commune (fiches inactives).'),
 'Martinique – activité mobile': ([], '', 'Activité mobile', 'Professionnel mobile : aucune commune ni point.'),
 'À localiser': ([], '', '', 'Placeholder : localisation inconnue, aucune commune (fiches inactives).'),
 'Pitons du Carbet': ([], 'Pitons du Carbet', '', 'Massif à cheval sur plusieurs communes : communes non déduites, à préciser si utile (fiches inactives).'),
 'Fort-de-France / Nord': (['Fort-de-France'], '', 'Nord', 'Commune + zone : vérifier que « Nord » désigne bien une zone d\'intervention.'),
 "Anses-d'Arlet / Caravelle": (["Les Anses-d'Arlet", 'La Trinité'], 'Caravelle', '', 'Caravelle rattachée à La Trinité (presqu\'île) : à confirmer.'),
 'Tartane (La Trinité)': (['La Trinité'], 'Tartane', '', 'Ordre inversé lieu-dit / commune dans la source.'),
 "Grande Anse d'Arlet (Les Anses-d'Arlet)": (["Les Anses-d'Arlet"], "Grande Anse d'Arlet", '', 'Ordre inversé lieu-dit / commune dans la source.'),
}
wb=openpyxl.load_workbook('docs/data/EKOTRIP_FINAL.xlsx',read_only=True)
rows=list(wb['01_FICHES_APP'].iter_rows(values_only=True)); h=rows[0]
V=collections.OrderedDict()
for r in rows[1:]:
    d=dict(zip(h,r)); raw=str(d['commune']).strip() if d['commune'] is not None else '(vide)'
    v=V.setdefault(raw,{'total':0,'actives':0,'ids':[],'cp':set()})
    v['total']+=1; v['ids'].append(d['id'])
    if d['is_active'] is True: v['actives']+=1
    if d['code_postal']: v['cp'].add(str(d['code_postal']).strip())

out=[]; unresolved=[]
for raw in sorted(V, key=lambda s: key(s) or s):
    v=V[raw]
    if raw in MANUAL:
        names,lieu,zone,com=MANUAL[raw]; valider='OUI'
        typ=('COMMUNE_ZONE' if names and zone else 'ZONE' if zone else 'MULTI' if len(names)>1 else 'LIEU_DIT' if lieu else 'SANS_COMMUNE')
    else:
        lieu=''; zone=''; com=''
        m=re.match(r'^(.*?)\s*\((.+)\)\s*$',raw)
        base=raw
        if m: base,lieu=m.group(1),m.group(2)
        parts=[p.strip() for p in re.split(r'\s*/\s*',base)]
        names=[]
        for p in parts:
            k=key(p)
            if k in OFFK: names.append(OFFK[k][1])
            else: unresolved.append(raw)
        if lieu and key(lieu) in OFFK and len(names)==0: pass
        if len(names)>1: typ='MULTI'; valider='NON'; com=com or 'Activité / site sur plusieurs communes : filtre sur chacune.'
        elif lieu: typ='LIEU_DIT'; valider='NON'
        elif names and names[0]==raw: typ='EXACT'; valider='NON'
        else: typ='VARIANTE'; valider='NON'; com=com or f'Variante d\'écriture de « {names[0]} ».'
    codes=[OFFK[key(n)][0] for n in names]
    out.append({'valeur_brute':raw,'type':typ,'communes_officielles':' | '.join(names),'codes_insee':' | '.join(codes),
        'lieu_dit':lieu,'zone':zone,'a_valider':valider,'nb_fiches':v['total'],'nb_fiches_actives':v['actives'],
        'codes_postaux_observes':' | '.join(sorted(v['cp'])),'exemples_ids':' '.join(v['ids'][:5]),'commentaire':com})
assert not unresolved, unresolved
cols=list(out[0].keys())
with open(OUT+'COMMUNES_CORRESPONDANCE_PROPOSITION.csv','w',newline='',encoding='utf-8-sig') as f:
    w=csv.DictWriter(f,cols,delimiter=';'); w.writeheader(); w.writerows(out)
with open(OUT+'COMMUNES_OFFICIELLES_INSEE_2026.csv','w',newline='',encoding='utf-8-sig') as f:
    w=csv.writer(f,delimiter=';'); w.writerow(['code_insee','nom_officiel']); [w.writerow(x) for x in sorted(OFF)]
c=collections.Counter(o['type'] for o in out); print(len(out), dict(c), 'à valider:', sum(o['a_valider']=='OUI' for o in out))
used={n for o in out for n in o['communes_officielles'].split(' | ') if n}; print('communes officielles sans aucune fiche :', [n for _,n in OFF if n not in used])
