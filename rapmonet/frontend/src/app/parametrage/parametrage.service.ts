import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

export interface AttributFormat {
  code: string;
  libelle: string;
  position: number;
  taille: number;
  pad: string;
  decimales: number;
  type: string;
  specifique: boolean;
  confidentiel: boolean;
}
export interface TcrFormat { code: string; libelle: string; rang: number; attributs: AttributFormat[] }
export interface TcFormat { code: string; libelle: string; description: string | null; longueur: number; tcrs: TcrFormat[] }
export interface SousFichierFormat {
  code: string; libelle: string; description: string | null; tcEntete: string; tcFin: string; tcAutorises: string[];
}
export interface FormatFichier {
  code: string;
  famille: string;
  libelle: string;
  longueurEnregistrement: number;
  zoneTc: { position: number; longueur: number };
  zoneSequence: { position: number; longueur: number };
  zoneTcr: { position: number; longueur: number };
  positionDonnees: number;
  tcEntete: string;
  tcFin: string;
  sousFichiers: SousFichierFormat[];
  tcs: Record<string, TcFormat>;
}

@Injectable({ providedIn: 'root' })
export class ParametrageService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<FormatFichier>>();

  format(code: string): Observable<FormatFichier> {
    if (!this.cache.has(code)) {
      this.cache.set(code, this.http.get<FormatFichier>(`/api/parametrage/formats/${code}`).pipe(shareReplay(1)));
    }
    return this.cache.get(code)!;
  }
}
