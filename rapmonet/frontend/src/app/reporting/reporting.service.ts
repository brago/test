import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type Axe = 'GAB' | 'TPE' | 'CARTE';
export interface Indicateur { code: string; axe: Axe; libelle: string; description: string; }
export interface LigneKpi { entite: string; libelle: string | null; nombres: number[]; montants: number[]; totalNombre: number; totalMontant: number; }
export interface Kpi {
  indicateur: string; libelle: string; description: string; axe: Axe; mois: string[]; lignes: LigneKpi[];
  totauxNombre: number[]; totauxMontant: number[]; totalNombre: number; totalMontant: number; avertissement: string | null;
}
export interface LigneTrimestre { section: string; rubrique: string; reseau: string; nombre: number; montant: number; entites: number; }
export interface EtatTrimestriel { annee: number; trimestre: number; du: string; au: string; lignes: LigneTrimestre[]; avertissement: string; }

@Injectable({ providedIn: 'root' })
export class ReportingService {
  private readonly http = inject(HttpClient);

  indicateurs(): Observable<Indicateur[]> {
    return this.http.get<Indicateur[]>('/api/reporting/kpi/indicateurs');
  }

  kpi(indicateur: string, du: string, au: string): Observable<Kpi> {
    return this.http.get<Kpi>('/api/reporting/kpi', { params: { indicateur, du, au } });
  }

  trimestre(annee: number, trimestre: number): Observable<EtatTrimestriel> {
    return this.http.get<EtatTrimestriel>('/api/reporting/reglementaire/trimestre', { params: { annee, trimestre } });
  }
}
