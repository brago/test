import re, sys, json
txt = open(sys.argv[1], encoding='utf-8').read().split('\n')
sec_re = re.compile(r'^\s*(7\.\d+)\s+(TC .*?/TCR \d.*?FORMAT|TC 46)\s*$')
fld_re = re.compile(r'^\s*(\d{3})\s{2,}(.+?)\s{2,}(\d{2,3})\s+([A-Z]{1,3})\s+(\d{2,3})(?:\s{2,}(.*))?$')
noise = re.compile(r'Projet :|Draft|Status|Validé|SPC/LIS|CONFIDENTIEL|^\s*N°\s+NOM')
sections = []; cur = None; start = False
for i, l in enumerate(txt):
    if i < 2400: continue
    m = sec_re.match(l)
    if m:
        cur = {'id': m.group(1), 'titre': m.group(2).strip(), 'ligne': i+1, 'champs': []}
        sections.append(cur); continue
    if cur is None or noise.search(l): continue
    f = fld_re.match(l)
    if f:
        cur['champs'].append({'n': f.group(1), 'nom': f.group(2).strip(), 'pos': int(f.group(3)),
                              'type': f.group(4), 'lg': int(f.group(5)), 'com': (f.group(6) or '').strip()})
    elif cur['champs'] and l.strip():
        # suite du nom (colonne nom) ou du commentaire
        parts = re.split(r'\s{3,}', l.strip())
        last = cur['champs'][-1]
        indent = len(l) - len(l.lstrip())
        if indent < 30 and len(parts[0]) < 40 and not last.get('fin_nom'):
            last['nom'] += ' ' + parts[0]
            if len(parts) > 1: last['com'] += ' ' + ' '.join(parts[1:])
        else:
            last['com'] += ' ' + ' '.join(parts)
for s in sections:
    c = s['champs']; errs = []
    p = 1
    for f in c:
        if f['pos'] != p: errs.append(f"{f['n']} pos {f['pos']} attendu {p}")
        p = f['pos'] + f['lg']
    s['fin'] = p - 1; s['erreurs'] = errs
json.dump(sections, open(sys.argv[2], 'w'), ensure_ascii=False, indent=1)
for s in sections:
    print(f"{s['id']:5} {s['titre'][:55]:55} champs={len(s['champs']):3} fin={s['fin']:3} {'OK' if not s['erreurs'] and s['fin']==256 else 'KO '+'; '.join(s['erreurs'][:3])}")
