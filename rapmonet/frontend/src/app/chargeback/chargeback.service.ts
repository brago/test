import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResultat } from '../exploration/exploration-lis.service';
import { OperationGab, SessionGab } from '../core/ejournal';

export type SensChargeback = 'emission' | 'acquisition';
export interface LigneCycle { etape: string; situation: string; date: string | null; alerte: boolean; }
export interface OperationCycle {
  journee: string; fichier: string; tc: string; libelle: string; usage: string | null; montant: number | null;
  motif: string | null; message: string | null;
}
export interface Dossier {
  id: number; sens: 'EMISSION' | 'ACQUISITION'; arn: string; tc: string; libelleTc: string; rrn: string | null;
  carte: string | null; terminal: string | null; marchand: string | null; dateTransaction: string | null;
  heureTransaction: string | null; autorisation: string | null; montant: number | null; devise: string | null;
  motif: string | null; referenceChargeback: string | null; journeeChargeback: string;
  etat: 'EN_ATTENTE' | 'REPRESENTE' | 'SECOND_CHARGEBACK' | 'ANNULE' | 'DELAI_ECOULE'; situation: string;
  joursEcoules: number; echeance: string; regularisation: 'NON' | 'OUI' | 'REJET'; commentaire: string | null;
  regularisePar: string | null; regulariseLe: string | null; archive: boolean;
  cycle: LigneCycle[] | null; operations: OperationCycle[] | null;
}
export interface JournalGab {
  conclusion: string; billetsPris: boolean; operations: OperationGab[]; session: SessionGab | null; recherche: string | null;
}
export interface CritereChargeback { du?: string; au?: string; reference?: string; regularisation?: string; archives?: string; }

function params(o: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  return p;
}

@Injectable({ providedIn: 'root' })
export class ChargebackService {
  private readonly http = inject(HttpClient);

  rechercher(sens: SensChargeback, c: CritereChargeback, page: number, taille: number):
    Observable<{ page: PageResultat<Dossier>; binsParametres: boolean }> {
    return this.http.get<{ page: PageResultat<Dossier>; binsParametres: boolean }>(`/api/chargebacks/${sens}`,
      { params: params({ ...c, page, taille }) });
  }

  dossier(sens: SensChargeback, id: number): Observable<Dossier> {
    return this.http.get<Dossier>(`/api/chargebacks/${sens}/${id}`);
  }

  regulariser(sens: SensChargeback, id: number, statut: string, commentaire: string): Observable<Dossier> {
    return this.http.post<Dossier>(`/api/chargebacks/${sens}/${id}/regularisation`, { statut, commentaire });
  }

  archiver(sens: SensChargeback): Observable<{ archives: number }> {
    return this.http.post<{ archives: number }>(`/api/chargebacks/${sens}/archivage`, null);
  }

  journalGab(id: number): Observable<JournalGab> {
    return this.http.get<JournalGab>(`/api/chargebacks/acquisition/${id}/journal-gab`);
  }
}
