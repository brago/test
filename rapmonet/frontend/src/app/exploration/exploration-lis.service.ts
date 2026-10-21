import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TypeLis } from '../ingestion/lis.service';

export interface PageResultat<T> { elements: T[]; total: number; page: number; taille: number }

export interface TransactionLis {
  id: number;
  journee: string;
  typeLis: TypeLis;
  nomFichier: string;
  sousFichier: string;
  tc: string;
  libelleTc: string | null;
  sequence: number;
  nbTcr: number;
  attributs: Record<string, unknown>;
}
export interface ValeurAttribut {
  code: string; libelle: string; position: number; taille: number; brut: string; valeur: unknown; erreur: string | null;
}
export interface LigneDetail { sequence: number; tcr: string; contenu: string; attributs: ValeurAttribut[] }
export interface DetailTransaction { transaction: TransactionLis; lignes: LigneDetail[] }

export interface CritereLis { du?: string; au?: string; typeLis?: TypeLis | ''; tc?: string }

@Injectable({ providedIn: 'root' })
export class ExplorationLisService {
  private readonly http = inject(HttpClient);

  rechercher(critere: CritereLis, page: number, taille: number): Observable<PageResultat<TransactionLis>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    for (const [k, v] of Object.entries(critere)) {
      if (v) params = params.set(k, v);
    }
    return this.http.get<PageResultat<TransactionLis>>('/api/exploration/lis/transactions', { params });
  }

  detail(id: number): Observable<DetailTransaction> {
    return this.http.get<DetailTransaction>(`/api/exploration/lis/transactions/${id}`);
  }
}
