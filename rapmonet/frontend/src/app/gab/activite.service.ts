import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Signalement { code: 'TAUX_REFUS' | 'INACTIVITE' | 'CARTES_CAPTUREES' | 'JOURS_SANS_JOURNAL'; libelle: string; }
export interface LigneActiviteGab {
  terminal: string; libelle: string | null; localisation: string | null; agence: string | null; parametre: boolean; actif: boolean;
  joursCouverts: number; joursPeriode: number; sessions: number; retraits: number; montant: number; montantMoyenJour: number;
  refuses: number; tauxRefus: number; motifPrincipal: string | null; nonPris: number; cartesCapturees: number; annulations: number;
  incidentsEmv: number; plusLongueInactivite: number; dateInactivite: string | null; debutInactivite: string | null;
  dernierJournal: string | null; signalements: Signalement[];
}
export interface PointActivite { libelle: string; nombre: number; montant: number; }
export interface MotifRefus { code: string; libelle: string; nombre: number; }
export interface SeuilsActivite { debutPlage: string; finPlage: string; inactiviteMinutes: number; tauxRefus: number; tentativesMin: number; }
export interface ActiviteGabs {
  du: string; au: string; seuils: SeuilsActivite;
  synthese: { gabs: number; gabsAvecActivite: number; gabsSignales: number; retraits: number; montant: number; refuses: number;
    tauxRefus: number; nonPris: number; cartesCapturees: number };
  gabs: LigneActiviteGab[]; parJour: PointActivite[]; parHeure: PointActivite[]; refus: MotifRefus[];
}
export interface JourGab {
  terminal: string; date: string; sessions: number; retraitsDistribues: number; montantDistribue: number; retraitsRefuses: number;
  refusParCode: Record<string, number>; soldes: number; billetsNonPris: number; cartesCapturees: number; annulations: number;
  incidentsEmv: number; retraitsParHeure: number[]; premierPassage: string | null; dernierPassage: string | null;
  plusLongueInactivite: number; debutInactivite: string | null; arretes: number; ajoutsEspeces: number;
}
export interface DetailActiviteGab {
  gab: LigneActiviteGab; jours: { jour: JourGab; tauxRefus: number; signalements: Signalement[] }[];
  joursSansJournal: string[]; parHeure: PointActivite[]; refus: MotifRefus[];
}

@Injectable({ providedIn: 'root' })
export class ActiviteGabService {
  private readonly http = inject(HttpClient);

  activite(du: string, au: string, terminal: string): Observable<ActiviteGabs> {
    return this.http.get<ActiviteGabs>('/api/gab/activite', { params: { du, au, terminal } });
  }

  detail(terminal: string, du: string, au: string): Observable<DetailActiviteGab> {
    return this.http.get<DetailActiviteGab>(`/api/gab/activite/${encodeURIComponent(terminal)}`, { params: { du, au } });
  }
}
