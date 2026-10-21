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
  montant: number; ecartMinutes: number | null; traitement: TraitementEcart | null; commentaire: string | null;
  traitePar: string | null; traiteLe: string | null;
}

export interface SyntheseRapprochement {
  retraitsConfreres: number; montantRetraitsConfreres: number; retraitsOnUs: number; montantOnUs: number;
  avisLis: number; montantAvisLis: number; rapproches: number; montantRapproche: number;
  nonRemontes: number; montantNonRemonte: number; avisSansRetrait: number; montantAvisSansRetrait: number;
  enAttente: number; montantEnAttente: number; journalAbsent: number; montantJournalAbsent: number;
  ecartsATraiter: number; derniereJourneeLis: string | null; toleranceMinutes: number; delaiLisJours: number;
}

export interface Rapprochement { synthese: SyntheseRapprochement; lignes: LigneRapprochement[]; }

@Injectable({ providedIn: 'root' })
export class RapprochementService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/rapprochement/gab-lis';

  rapprocher(du: string, au: string, terminal: string, vue: VueRapprochement): Observable<Rapprochement> {
    return this.http.get<Rapprochement>(this.url, { params: { du, au, terminal, vue } });
  }

  traiter(l: LigneRapprochement, statut: TraitementEcart, commentaire: string): Observable<void> {
    return this.http.post<void>(`${this.url}/ecarts`, { cle: l.cle, typeEcart: l.resultat, statut, commentaire });
  }
}
