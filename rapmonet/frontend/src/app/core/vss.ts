import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { AnomalieLis } from '../ingestion/lis.service';

export interface SyntheseVss {
  deviseReglement: string | null;
  montantNet: number | null;
  nbTransactions: number | null;
  deviseCompensation: string | null;
  montantCompensation: number | null;
}
export interface ResultatVss {
  integre: boolean;
  id: number | null;
  nom: string;
  dateTraitement: string | null;
  sreId: string | null;
  sreNom: string | null;
  synthese: SyntheseVss;
  rapports: { code: string; titre: string; nbLignes: number; sansDonnees: boolean; ignore: boolean }[];
  anomalies: AnomalieLis[];
}
export interface FichierVss {
  id: number; nom: string; dateTraitement: string; sreId: string | null; sreNom: string | null; nbRapports: number;
  nbLignes: number; synthese: SyntheseVss; nbAvertissements: number; integreLe: string; integrePar: string | null;
}
export interface ValeurVss { nombre: number | null; sens: 'DB' | 'CR' | null; texte: string | null }
export interface ResumeRapportVss {
  id: number; fichierId: number; nomFichier: string; code: string; libelleType: string | null; titre: string;
  dateTraitement: string; sreId: string; sreNom: string; nbPages: number; sansDonnees: boolean; nbLignes: number;
}
export interface LigneRapportVss {
  rang: number; page: number; niveau: number; chemin: string; libelle: string;
  deviseReglement: string | null; deviseCompensation: string | null; valeurs: Record<string, ValeurVss>;
}
export interface RapportVss {
  resume: ResumeRapportVss; rollup: string | null; entiteFonds: string | null; colonnes: string[]; lignes: LigneRapportVss[];
}
export interface TypeRapportVss { code: string; libelle: string; description: string | null; integre: boolean }

/** Accès aux API VSS (chargement, consultation, paramétrage). */
@Injectable({ providedIn: 'root' })
export class VssService {
  private readonly http = inject(HttpClient);

  integrer(fichier: File): Observable<ResultatVss> {
    const data = new FormData();
    data.append('fichier', fichier, fichier.name);
    return this.http.post<ResultatVss>('/api/ingestion/vss', data).pipe(
      catchError((e: HttpErrorResponse) => (e.status === 422 && e.error?.anomalies ? of(e.error as ResultatVss) : throwError(() => e))),
    );
  }

  fichiers(): Observable<FichierVss[]> {
    return this.http.get<FichierVss[]>('/api/ingestion/vss');
  }

  anomalies(id: number): Observable<AnomalieLis[]> {
    return this.http.get<AnomalieLis[]>(`/api/ingestion/vss/${id}/anomalies`);
  }

  annuler(id: number): Observable<void> {
    return this.http.delete<void>(`/api/ingestion/vss/${id}`);
  }

  rapports(critere: { du?: string; au?: string; code?: string }): Observable<ResumeRapportVss[]> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(critere)) if (v) params = params.set(k, v);
    return this.http.get<ResumeRapportVss[]>('/api/exploration/vss/rapports', { params });
  }

  rapport(id: number): Observable<RapportVss> {
    return this.http.get<RapportVss>(`/api/exploration/vss/rapports/${id}`);
  }

  types(): Observable<TypeRapportVss[]> {
    return this.http.get<TypeRapportVss[]>('/api/parametrage/vss/types-rapports');
  }

  modifierType(code: string, integre: boolean): Observable<TypeRapportVss> {
    return this.http.put<TypeRapportVss>(`/api/parametrage/vss/types-rapports/${code}`, { integre });
  }
}

const formats = new Map<number, Intl.NumberFormat>();
function nombre(n: number, decimales: number): string {
  if (!formats.has(decimales)) {
    formats.set(decimales, new Intl.NumberFormat('fr-FR', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }));
  }
  return formats.get(decimales)!.format(n);
}

/**
 * Affiche une valeur de rapport VSS comme Visa : « 2 183,96 DB ».
 * Colonnes COUNT : entier ; colonnes CLEARING (XOF) : sans décimale ; autres : 2 décimales.
 */
export function afficherValeurVss(colonne: string, v: ValeurVss | undefined): string {
  if (!v) return '';
  if (v.nombre === null) return v.texte ?? '';
  const decimales = colonne.includes('COUNT') || colonne.includes('CLEARING') ? 0 : 2;
  return nombre(v.nombre, decimales) + (v.sens ? ' ' + v.sens : '');
}

/** Montant signé de la synthèse (négatif = débit) affiché avec DB / CR. */
export function afficherMontantSigne(montant: number | null, devise: string | null, decimales = 2): string {
  if (montant === null || montant === undefined) return '–';
  const sens = montant < 0 ? 'DB' : montant > 0 ? 'CR' : '';
  return `${nombre(Math.abs(montant), decimales)} ${sens} ${devise ?? ''}`.replace(/\s+/g, ' ').trim();
}
