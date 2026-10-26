import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ResultatRapprochement = 'RAPPROCHE' | 'RETRAIT_NON_REMONTE' | 'AVIS_SANS_RETRAIT' | 'EN_ATTENTE_LIS' | 'JOURNAL_ABSENT';
export type VueRapprochement = 'ECARTS' | 'RAPPROCHES' | 'TOUS';
export type TraitementEcart = 'A_TRAITER' | 'JUSTIFIE' | 'REGULARISE';

export interface LigneRapprochement {
  cle: string; resultat: ResultatRapprochement; libelleResultat: string; terminal: string | null; date: string;
  dateHeureJournal: string | null; referenceJournal: string | null; dateHeureLis: string | null; autorisation: string | null;
  rrn: string | null; reseau: string | null; journeeLis: string | null; fichierLis: string | null; carte: string | null;
  montant: number; montantLis: number | null; ecartMinutes: number | null; traitement: TraitementEcart | null; commentaire: string | null;
  traitePar: string | null; traiteLe: string | null;
}

export interface SyntheseRapprochement {
  retraitsConfreres: number; montantRetraitsConfreres: number; retraitsOnUs: number; montantOnUs: number;
  avisLis: number; montantAvisLis: number; rapproches: number; montantRapproche: number;
  nonRemontes: number; montantNonRemonte: number; avisSansRetrait: number; montantAvisSansRetrait: number;
  enAttente: number; montantEnAttente: number; journalAbsent: number; montantJournalAbsent: number;
  ecartsATraiter: number; derniereJourneeLis: string | null; toleranceMinutes: number; delaiLisJours: number;
  regles: ReglesGabLis;
}

export type ChampReference = 'AUTORISATION' | 'BORDEREAU' | 'RRN';
export interface ReglesGabLis {
  toleranceMinutes: number; delaiLisJours: number; memeCarte: boolean; toleranceMontant: number; reference: boolean;
  champsReference: ChampReference[]; fenetreReferenceJours: number;
}
export interface ChangementRapprochement { terminal: string | null; date: string; carte: string | null; montant: number; avant: string | null; apres: string }
export interface SimulationRegles {
  actuelle: SyntheseRapprochement; proposee: SyntheseRapprochement; changements: ChangementRapprochement[]; nbChangements: number;
}
export interface ModificationRegles { modifieLe: string; modifiePar: string | null; motif: string; avant: string; apres: string }

export interface Rapprochement { synthese: SyntheseRapprochement; lignes: LigneRapprochement[]; }

@Injectable({ providedIn: 'root' })
export class RapprochementService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/rapprochement/gab-lis';

  rapprocher(du: string, au: string, terminal: string, vue: VueRapprochement): Observable<Rapprochement> {
    return this.http.get<Rapprochement>(this.url, { params: { du, au, terminal, vue } });
  }

  regles(): Observable<ReglesGabLis> {
    return this.http.get<ReglesGabLis>('/api/rapprochement/regles/gab-lis');
  }

  simuler(regles: ReglesGabLis, du: string, au: string, terminal: string): Observable<SimulationRegles> {
    return this.http.post<SimulationRegles>('/api/rapprochement/regles/gab-lis/simulation', { regles, du, au, terminal });
  }

  modifierRegles(regles: ReglesGabLis, motif: string): Observable<ReglesGabLis> {
    return this.http.put<ReglesGabLis>('/api/rapprochement/regles/gab-lis', { regles, motif });
  }

  historiqueRegles(): Observable<ModificationRegles[]> {
    return this.http.get<ModificationRegles[]>('/api/rapprochement/regles/gab-lis/historique');
  }

  traiter(l: LigneRapprochement, statut: TraitementEcart, commentaire: string): Observable<void> {
    return this.http.post<void>(`${this.url}/ecarts`, { cle: l.cle, typeEcart: l.resultat, statut, commentaire });
  }
}
