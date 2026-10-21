import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResultat } from '../exploration/exploration-lis.service';

export interface Cassette { cassette: string; coupure: number; type: number; }
export interface ProfilGab { code: string; libelle: string; cassettes: Cassette[]; }
export interface Gab {
  terminalId: string; libelle: string; localisation: string | null; agence: string | null; marque: string; profilCode: string;
  compteCaisse: string | null; compteManquant: string | null; compteExcedent: string | null; actif: boolean;
}
export interface BinCarte { bin: string; libelle: string; typeCarte: 'DEBIT' | 'CREDIT' | 'PREPAYEE'; }
export interface PlageGim { prefixe: string; debut: string; fin: string; pays: string; libellePays: string; }
export interface EtatTableGim {
  fichier: string | null; importeLe: string | null; importePar: string | null; plages: number;
  parPays: { pays: string; libellePays: string; plages: number }[];
}
export interface RapportImportGim { fichier: string; plages: number; lignesIgnorees: number; anomalies: string[]; }
export interface ControleBin { bin: string; plagesCouvertes: number; pays: string[]; autresPlagesDuPrefixe: number; paysAutresPlages: string[]; }
export interface PropositionBin { bin: string; operations: number; plagesGim: number; pays: string[]; prefixePartage: boolean; }

export type StatutGab = 'SAISI' | 'VALIDE' | 'ANNULE';
export const LIBELLES_STATUT_GAB: Record<string, string> = { SAISI: 'À valider', VALIDE: 'Validé', ANNULE: 'Annulé' };

/** Passage du gabiste lu dans le journal : compteurs avant remise à zéro et billets ajoutés, par type 1 à 4. */
export interface PassageJournal {
  dateHeure: string; actions: string[]; cassettes: number[] | null; rejets: number[] | null; ajoutes: number[] | null;
}

export interface LigneBilletage { cassette: string; type: number; coupure: number; nombre: number; montant: number; }
export interface ControleJournal {
  statut: 'CONCORDANT' | 'ECART' | 'ABSENT'; passage: string | null; journal: number[] | null; saisie: number[];
  montantJournal: number | null; message: string;
}
export interface Appro {
  id: number; reference: string; terminal: string; libelleGab: string; agence: string | null; compteCaisse: string | null;
  dateAppro: string; gabiste: string; referenceExterne: string | null; numeroBon: string | null; commentaire: string | null;
  billetage: LigneBilletage[]; montant: number; statut: StatutGab; saisiPar: string; saisiLe: string; modifiePar: string;
  decidePar: string | null; decideLe: string | null; motif: string | null; arrete: string | null;
  controleJournal: ControleJournal | null;
}
export interface SaisieAppro {
  terminal: string; dateAppro: string; gabiste: string; referenceExterne: string; numeroBon: string; commentaire: string;
  billets: Record<string, number>;
}

export interface LigneJournalArrete { type: number; coupure: number; cassette: number; rejet: number; montant: number; }
export interface LigneInventaire { cassette: string; type: number; coupure: number; nombre: number; rejet: number; montant: number; }
export interface Compte { nombre: number; rejet: number; }
export interface Arrete {
  id: number; reference: string; terminal: string; libelleGab: string; agence: string | null; compteCaisse: string | null;
  compteManquant: string | null; compteExcedent: string | null; approId: number; approReference: string; dateAppro: string;
  montantAppro: number; dateArrete: string; gabiste: string; commentaire: string | null; journal: LigneJournalArrete[];
  inventaire: LigneInventaire[]; transactionsAttente: number; soldeComptable: number | null; montantJournal: number;
  montantPhysique: number; ecartPhysiqueJournal: number; ecartPhysiqueComptable: number | null; retraitsJournal: number | null;
  soldeTheorique: number | null; ecartJournalTheorique: number | null; statut: StatutGab; saisiPar: string; saisiLe: string;
  modifiePar: string; decidePar: string | null; decideLe: string | null; motif: string | null;
  couverture: Couverture | null;
}
/** Jours de la période (appro → arrêté) pour lesquels un journal du GAB est intégré. */
export interface Couverture { joursCouverts: number; joursPeriode: number; }
export interface SaisieArrete {
  terminal: string; approId: number | null; dateArrete: string; gabiste: string; commentaire: string;
  journal: Record<number, Compte>; inventaire: Record<string, Compte>; transactionsAttente: number; soldeComptable: number | null;
}
export interface PreparationArrete {
  gab: Gab; cassettes: Cassette[]; coupures: number[]; approsNonArretes: Appro[]; passage: PassageJournal | null;
  retraitsJournal: number | null; couverture: Couverture | null;
}
export interface CritereGab { du?: string; au?: string; terminal?: string; statut?: string; }

function params(o: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  return p;
}

/** Date et heure locales au format AAAA-MM-JJTHH:mm:00 (LocalDateTime côté serveur). */
export function dateHeureIso(jour: Date | null, heure: string): string {
  if (!jour) return '';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${jour.getFullYear()}-${p(jour.getMonth() + 1)}-${p(jour.getDate())}T${heure || '00:00'}:00`;
}

@Injectable({ providedIn: 'root' })
export class GabService {
  private readonly http = inject(HttpClient);
  private readonly p = '/api/parametrage/gab';

  gabs(): Observable<Gab[]> { return this.http.get<Gab[]>(`${this.p}/terminaux`); }
  enregistrerGab(g: Gab): Observable<Gab> { return this.http.put<Gab>(`${this.p}/terminaux/${encodeURIComponent(g.terminalId)}`, g); }
  supprimerGab(id: string): Observable<void> { return this.http.delete<void>(`${this.p}/terminaux/${encodeURIComponent(id)}`); }
  profils(): Observable<ProfilGab[]> { return this.http.get<ProfilGab[]>(`${this.p}/profils`); }
  enregistrerProfil(p: ProfilGab): Observable<ProfilGab> { return this.http.put<ProfilGab>(`${this.p}/profils/${encodeURIComponent(p.code)}`, p); }
  supprimerProfil(code: string): Observable<void> { return this.http.delete<void>(`${this.p}/profils/${encodeURIComponent(code)}`); }
  bins(): Observable<BinCarte[]> { return this.http.get<BinCarte[]>(`${this.p}/bins`); }
  enregistrerBin(b: BinCarte): Observable<BinCarte> { return this.http.put<BinCarte>(`${this.p}/bins/${b.bin}`, b); }
  supprimerBin(bin: string): Observable<void> { return this.http.delete<void>(`${this.p}/bins/${bin}`); }
  etatTableGim(): Observable<EtatTableGim> { return this.http.get<EtatTableGim>(`${this.p}/bins/table-gim`); }
  importerTableGim(f: File): Observable<RapportImportGim> {
    const d = new FormData();
    d.append('fichier', f, f.name);
    return this.http.post<RapportImportGim>(`${this.p}/bins/table-gim`, d);
  }
  plagesGim(recherche: string, pays: string): Observable<PlageGim[]> {
    return this.http.get<PlageGim[]>(`${this.p}/bins/table-gim/plages`, { params: { recherche, pays } });
  }
  controlesBins(): Observable<ControleBin[]> { return this.http.get<ControleBin[]>(`${this.p}/bins/controles`); }
  propositionsBins(): Observable<PropositionBin[]> { return this.http.get<PropositionBin[]>(`${this.p}/bins/propositions`); }

  appros(c: CritereGab, page: number, taille: number): Observable<PageResultat<Appro>> {
    return this.http.get<PageResultat<Appro>>('/api/gab/appros', { params: params({ ...c, page, taille }) });
  }
  appro(id: number): Observable<Appro> { return this.http.get<Appro>(`/api/gab/appros/${id}`); }
  preparationAppro(terminal: string, date: string): Observable<{ gab: Gab; cassettes: Cassette[]; passages: PassageJournal[] }> {
    return this.http.get<{ gab: Gab; cassettes: Cassette[]; passages: PassageJournal[] }>('/api/gab/appros/preparation', { params: params({ terminal, date }) });
  }
  creerAppro(s: SaisieAppro): Observable<Appro> { return this.http.post<Appro>('/api/gab/appros', s); }
  modifierAppro(id: number, s: SaisieAppro): Observable<Appro> { return this.http.put<Appro>(`/api/gab/appros/${id}`, s); }

  arretes(c: CritereGab, page: number, taille: number): Observable<PageResultat<Arrete>> {
    return this.http.get<PageResultat<Arrete>>('/api/gab/arretes', { params: params({ ...c, page, taille }) });
  }
  arrete(id: number): Observable<Arrete> { return this.http.get<Arrete>(`/api/gab/arretes/${id}`); }
  preparationArrete(terminal: string, date: string): Observable<PreparationArrete> {
    return this.http.get<PreparationArrete>('/api/gab/arretes/preparation', { params: params({ terminal, date }) });
  }
  creerArrete(s: SaisieArrete): Observable<Arrete> { return this.http.post<Arrete>('/api/gab/arretes', s); }
  modifierArrete(id: number, s: SaisieArrete): Observable<Arrete> { return this.http.put<Arrete>(`/api/gab/arretes/${id}`, s); }

  /** Validation ou annulation d'un appro ou d'un arrêté (menu « Validation »). */
  decider(objet: 'appros' | 'arretes', id: number, decision: 'validation' | 'annulation', motif?: string): Observable<unknown> {
    return this.http.post(`/api/gab/${objet}/${id}/${decision}`, decision === 'annulation' ? { motif } : null);
  }
}
