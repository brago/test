# Vérifie une palette de repères : contraste WCAG (icône blanche, fonds de carte)
# et distance CIEDE2000 en vision normale et sous simulation protan / deutan / tritan (Machado 2009).
# Usage : python3 docs/plans/palette/verifier_palette.py
import math, itertools, random
def hex2rgb(h): h=h.lstrip('#'); return tuple(int(h[i:i+2],16)/255 for i in (0,2,4))
def lin(c): return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
def delin(c): c=max(0,min(1,c)); return 12.92*c if c<=0.0031308 else 1.055*c**(1/2.4)-0.055
def lum(rgb): r,g,b=map(lin,rgb); return 0.2126*r+0.7152*g+0.0722*b
def contrast(a,b):
    la,lb=lum(a),lum(b); return (max(la,lb)+0.05)/(min(la,lb)+0.05)
# Machado et al. 2009, sévérité 1.0 (espace linéaire)
M={'protan':[[0.152286,1.052583,-0.204868],[0.114503,0.786281,0.099216],[-0.003882,-0.048116,1.051998]],
   'deutan':[[0.367322,0.860646,-0.227968],[0.280085,0.672501,0.047413],[-0.011820,0.042940,0.968881]],
   'tritan':[[1.255528,-0.076749,-0.178779],[-0.078411,0.930809,0.147602],[0.004733,0.691367,0.303900]]}
def sim(rgb,k):
    l=[lin(c) for c in rgb]; m=M[k]
    return tuple(delin(sum(m[i][j]*l[j] for j in range(3))) for i in range(3))
def lab(rgb):
    r,g,b=map(lin,rgb)
    x=(0.4124*r+0.3576*g+0.1805*b)/0.95047; y=0.2126*r+0.7152*g+0.0722*b; z=(0.0193*r+0.1192*g+0.9505*b)/1.08883
    f=lambda t: t**(1/3) if t>0.008856 else 7.787*t+16/116
    return (116*f(y)-16, 500*(f(x)-f(y)), 200*(f(y)-f(z)))
def de2000(c1,c2):
    L1,a1,b1=lab(c1); L2,a2,b2=lab(c2)
    C1=math.hypot(a1,b1); C2=math.hypot(a2,b2); Cb=(C1+C2)/2
    G=0.5*(1-math.sqrt(Cb**7/(Cb**7+25**7))); a1p=(1+G)*a1; a2p=(1+G)*a2
    C1p=math.hypot(a1p,b1); C2p=math.hypot(a2p,b2)
    h1=math.degrees(math.atan2(b1,a1p))%360; h2=math.degrees(math.atan2(b2,a2p))%360
    dL=L2-L1; dC=C2p-C1p
    dh=0 if C1p*C2p==0 else (h2-h1 if abs(h2-h1)<=180 else h2-h1-360 if h2>h1 else h2-h1+360)
    dH=2*math.sqrt(C1p*C2p)*math.sin(math.radians(dh/2))
    Lb=(L1+L2)/2; Cbp=(C1p+C2p)/2
    hb=(h1+h2)/2 if abs(h1-h2)<=180 else ((h1+h2+360)/2 if h1+h2<360 else (h1+h2-360)/2)
    if C1p*C2p==0: hb=h1+h2
    T=1-0.17*math.cos(math.radians(hb-30))+0.24*math.cos(math.radians(2*hb))+0.32*math.cos(math.radians(3*hb+6))-0.20*math.cos(math.radians(4*hb-63))
    SL=1+0.015*(Lb-50)**2/math.sqrt(20+(Lb-50)**2); SC=1+0.045*Cbp; SH=1+0.015*Cbp*T
    RT=-2*math.sqrt(Cbp**7/(Cbp**7+25**7))*math.sin(math.radians(60*math.exp(-((hb-275)/25)**2)))
    return math.sqrt((dL/SL)**2+(dC/SC)**2+(dH/SH)**2+RT*(dC/SC)*(dH/SH))
WHITE=(1,1,1)
BG={'terre (fond clair)':hex2rgb('#F2EFE9'),'végétation':hex2rgb('#DDE8CF'),'eau':hex2rgb('#A8D5E5')}
def report(pal):
    cols=[hex2rgb(h) for _,h,_ in pal]
    for (n,h,i),c in zip(pal,cols):
        print(f"{n:14} {h}  icône blanche {contrast(c,WHITE):4.1f}:1 | " + ' | '.join(f"{k} {contrast(c,v):3.1f}" for k,v in BG.items()))
    for k in ['normal','protan','deutan','tritan']:
        s=[c if k=='normal' else sim(c,k) for c in cols]
        pairs=[(de2000(s[i],s[j]),pal[i][0],pal[j][0]) for i,j in itertools.combinations(range(len(s)),2)]
        pairs.sort(); print(f"{k:7} ΔE2000 min {pairs[0][0]:5.1f} ({pairs[0][1]} / {pairs[0][2]}) ; 2e {pairs[1][0]:5.1f} ({pairs[1][1]} / {pairs[1][2]})")
import sys; exec(open(sys.argv[1]).read()) if len(sys.argv)>1 else None

if __name__ == '__main__' and len(sys.argv) == 1:
    # Palette proposée pour les groupes carte (docs/plans/palette/PALETTE_GROUPES_CARTE.md)
    report([('Dormir', '#0679AC', 'bed'), ('Nature', '#3E7446', 'tree'), ('Manger local', '#CC3711', 'utensils'),
            ('Découvrir', '#644321', 'landmark'), ('Bouger', '#284DB5', 'person-hiking'),
            ('Bien-être', '#93296F', 'spa'), ('Artisanat', '#4E6155', 'hand')])
    print('Groupe (regroupement) #2F3B45 : icône blanche', round(contrast(hex2rgb('#2F3B45'), WHITE), 1))
