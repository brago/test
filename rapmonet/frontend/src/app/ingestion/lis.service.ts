import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';

export type TypeLis = 'SW' | 'PR';

export interface AnomalieLis { ligne: number | null; gravite: 'BLOQUANTE' | 'AVERTISSEMENT'; message: string }

export interface RapportFichier {
  nom: string;
  typeLis: TypeLis;
  id: number | null;
  journee: string | null;
  nbEnregistrements: number;
  nbTransactions: number;
  repartitionTc: Record<string, number>;
  anomalies: AnomalieLis[];
}
export interface RapportIntegration { integre: boolean; fichiers: RapportFichier[] }

export interface FichierLis {
  id: number;
  nom: string;
  typeLis: TypeLis;
  journee: string;
  entete: Record<string, unknown>;
  nbEnregistrements: number;
  nbTransactions: number;
  repartitionTc: Record<string, number>;
  nbAvertissements: number;
  integreLe: string;
}

@Injectable({ providedIn: 'root' })
export class LisService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/ingestion/lis';

  /** Renvoie le rapport, que l'intégration réussisse (200) ou soit rejetée (422). */
  integrer(sw: File | null, pr: File | null): Observable<RapportIntegration> {
    const data = new FormData();
    if (sw) data.append('sw', sw, sw.name);
    if (pr) data.append('pr', pr, pr.name);
    return this.http.post<RapportIntegration>(this.url, data).pipe(
      catchError((e: HttpErrorResponse) => (e.status === 422 && e.error?.fichiers ? of(e.error as RapportIntegration) : throwError(() => e))),
    );
  }

  lister(): Observable<FichierLis[]> {
    return this.http.get<FichierLis[]>(this.url);
  }

  anomalies(id: number): Observable<AnomalieLis[]> {
    return this.http.get<AnomalieLis[]>(`${this.url}/${id}/anomalies`);
  }

  annuler(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
