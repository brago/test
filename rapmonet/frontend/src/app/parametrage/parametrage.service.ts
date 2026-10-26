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

export type Pad = 'ESPACE_DROITE' | 'ESPACE_GAUCHE' | 'ZERO_GAUCHE';
export type TypeDonnee = 'ALPHA' | 'NUMERIQUE' | 'DATE_JJMMAA' | 'DATE_AAMMJJ' | 'DATE_AAAAMMJJ' | 'DATE_JULIENNE_AAJJJ';
export const TYPES_DONNEE: TypeDonnee[] = ['ALPHA', 'NUMERIQUE', 'DATE_JJMMAA', 'DATE_AAMMJJ', 'DATE_AAAAMMJJ', 'DATE_JULIENNE_AAJJJ'];
export const PADS: { code: Pad; libelle: string }[] = [
  { code: 'ESPACE_DROITE', libelle: 'Espaces à droite' }, { code: 'ESPACE_GAUCHE', libelle: 'Espaces à gauche' },
  { code: 'ZERO_GAUCHE', libelle: 'Zéros à gauche' },
];

/** Champ du dictionnaire, avec les TC/TCR qui le contiennent. */
export interface Champ {
  code: string; libelle: string; description: string | null; taille: number; pad: Pad; decimales: number; type: TypeDonnee;
  specifique: boolean; confidentiel: boolean; protege: boolean; utilisations: string[];
}
export type SaisieChamp = Omit<Champ, 'protege' | 'utilisations'>;
export interface ModificationFormat {
  id: number; objet: string; action: string; motif: string; avant: string | null; apres: string; modifiePar: string | null; modifieLe: string;
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

  /** Relit le format après une modification. */
  recharger(code: string): Observable<FormatFichier> {
    this.cache.delete(code);
    return this.format(code);
  }

  champs(format: string): Observable<Champ[]> {
    return this.http.get<Champ[]>(`/api/parametrage/formats/${format}/champs`);
  }

  historique(format: string): Observable<ModificationFormat[]> {
    return this.http.get<ModificationFormat[]>(`/api/parametrage/formats/${format}/historique`);
  }

  modifierChamp(format: string, champ: SaisieChamp, motif: string): Observable<Champ> {
    return this.http.put<Champ>(`/api/parametrage/formats/${format}/champs/${champ.code}`, { champ, motif });
  }

  creerChamp(format: string, champ: SaisieChamp, motif: string): Observable<Champ> {
    return this.http.post<Champ>(`/api/parametrage/formats/${format}/champs`, { champ, motif });
  }

  modifierStructure(format: string, tc: string, tcr: string, champs: string[], motif: string): Observable<void> {
    return this.http.put<void>(`/api/parametrage/formats/${format}/tcs/${tc}/tcrs/${tcr}`, { champs, motif });
  }

  modifierTc(format: string, tc: string, libelle: string, description: string | null, motif: string): Observable<void> {
    return this.http.put<void>(`/api/parametrage/formats/${format}/tcs/${tc}`, { libelle, description, motif });
  }

  creerTc(format: string, tc: { code: string; libelle: string; description: string | null; tcrs: { code: string; libelle: string | null }[]; sousFichiers: string[] },
    motif: string): Observable<void> {
    return this.http.post<void>(`/api/parametrage/formats/${format}/tcs`, { tc, motif });
  }
}
