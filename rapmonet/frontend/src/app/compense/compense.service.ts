import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Commercant { terminalId: string; nom: string; compte: string; tauxGim: number; tauxVisa: number; tauxMci: number; actif: boolean; }
export interface RapportImport { lignes: number; crees: number; modifies: number; anomalies: string[]; }
export interface ParametresCompense {
  compteEmisOnus: string; compteAcqGim: string; compteAcqVisa: string; compteAcqMci: string; compteCompensationVisaMci: string;
  compteCompensationGim: string; compteCommission: string; compteTaf: string; compteCommissionGim: string; tauxTaf: number;
  codeOperation: string; devise: string;
}
export interface LigneCompense {
  achatId: number; reseau: 'GIM' | 'VISA' | 'MASTERCARD'; onUs: boolean; terminal: string; commercant: string; compte: string;
  compteDebit: string; carte: string | null; autorisation: string | null; rrn: string | null; arn: string | null;
  dateTransaction: string; heureTransaction: string | null; montant: number; taux: number; cci: number; frais: number;
  commission: number; taf: number; anomalie: string | null;
}
export interface TotalReseau { categorie: string; nombre: number; montant: number; frais: number; commission: number; taf: number; compteDebit: string; compteCredit: string; }
export interface Ecriture { compte: string; dateOperation: string; dateValeur: string; montant: number; libelle: string; }
export interface CompteMouvemente { compte: string; libelle: string; debit: number; credit: number; }
export interface Apercu { journee: string; lignes: LigneCompense[]; totaux: TotalReseau[]; dejaCompenses: number; anomalies: string[]; }
export interface Traitement {
  id: number; reference: string; journee: string; nbTransactions: number; montant: number; commission: number; taf: number;
  statut: 'SAISI' | 'VALIDE' | 'ANNULE'; creePar: string; creeLe: string; decidePar: string | null; decideLe: string | null;
  motif: string | null; parametres: ParametresCompense; lignes: LigneCompense[] | null; totaux: TotalReseau[] | null;
  ecritures: Ecriture[] | null; comptes: CompteMouvemente[] | null;
}

@Injectable({ providedIn: 'root' })
export class CompenseService {
  private readonly http = inject(HttpClient);

  commercants(): Observable<Commercant[]> { return this.http.get<Commercant[]>('/api/compense/commercants'); }
  enregistrer(c: Commercant): Observable<Commercant> { return this.http.put<Commercant>(`/api/compense/commercants/${encodeURIComponent(c.terminalId)}`, c); }
  supprimer(t: string): Observable<void> { return this.http.delete<void>(`/api/compense/commercants/${encodeURIComponent(t)}`); }
  importer(f: File): Observable<RapportImport> {
    const d = new FormData();
    d.append('fichier', f, f.name);
    return this.http.post<RapportImport>('/api/compense/commercants/import', d);
  }
  parametres(): Observable<ParametresCompense> { return this.http.get<ParametresCompense>('/api/compense/parametres'); }
  modifierParametres(p: ParametresCompense): Observable<ParametresCompense> { return this.http.put<ParametresCompense>('/api/compense/parametres', p); }

  journees(): Observable<string[]> { return this.http.get<string[]>('/api/compense/traitements/journees'); }
  apercu(journee: string): Observable<Apercu> { return this.http.get<Apercu>('/api/compense/traitements/apercu', { params: { journee } }); }
  creer(journee: string): Observable<Traitement> { return this.http.post<Traitement>('/api/compense/traitements', { journee }); }
  traitements(statut?: string): Observable<Traitement[]> {
    return this.http.get<Traitement[]>('/api/compense/traitements', { params: statut ? { statut } : {} });
  }
  traitement(id: number): Observable<Traitement> { return this.http.get<Traitement>(`/api/compense/traitements/${id}`); }
  decider(id: number, decision: 'validation' | 'annulation', motif?: string): Observable<Traitement> {
    return this.http.post<Traitement>(`/api/compense/traitements/${id}/${decision}`, decision === 'annulation' ? { motif } : null);
  }
}
