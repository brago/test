import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OperationGab } from '../core/ejournal';

export interface StatutRetrait { resultat: string; traitement: string | null; commentaire: string | null; autorisationLis: string | null; }
export interface TransactionLisPorteur {
  id: number; journee: string; typeLis: string; fichier: string; tc: string; libelleTc: string | null; date: string | null;
  montant: number | null; carte: string | null; libelle: string | null; terminal: string | null; autorisation: string | null; reference: string | null;
}
export interface ChargebackPorteur {
  id: number; sens: 'EMISSION' | 'ACQUISITION'; arn: string; libelleTc: string; rrn: string | null; carte: string | null;
  terminal: string | null; marchand: string | null; dateTransaction: string | null; montant: number | null; motif: string | null;
  journeeChargeback: string; etat: string | null; situation: string | null; regularisation: string; archive: boolean;
}
export interface ResumeReclamation {
  id: number; numero: string; carte: string | null; compte: string | null; client: string; objet: string; libelleObjet: string;
  statut: string; libelleStatut: string; echeance: string; enRetard: boolean; montantConteste: number | null; creeLe: string;
}
export interface ResultatPorteur {
  carteMasquee: string | null; exacte: boolean; compte: string | null; reference: string | null; du: string; au: string;
  operationsGab: { operation: OperationGab; rapprochement: StatutRetrait | null }[];
  transactionsLis: TransactionLisPorteur[]; chargebacks: ChargebackPorteur[]; reclamations: ResumeReclamation[]; tronque: boolean;
}
export interface OperationLiee { type: 'GAB' | 'LIS' | 'CHARGEBACK'; id: number; date: string | null; libelle: string; montant: number | null; }
export interface SaisieReclamation {
  carte: string | null; compte: string | null; client: string; contact: string | null; canal: string; objet: string;
  description: string; montantConteste: number | null; dateOperation: string | null; operations: OperationLiee[];
}
export interface Reclamation extends ResumeReclamation {
  contact: string | null; canal: string; libelleCanal: string; description: string; dateOperation: string | null;
  operations: OperationLiee[]; conclusion: string | null; creePar: string | null; cloturePar: string | null; clotureLe: string | null;
  suivi: { action: string; commentaire: string | null; auteur: string | null; le: string }[];
}
export interface Referentiels { objets: Record<string, string>; canaux: Record<string, string>; statuts: Record<string, string>; }

@Injectable({ providedIn: 'root' })
export class ReclamationService {
  private readonly http = inject(HttpClient);

  /** POST : le numéro de carte complet ne doit pas apparaître dans une adresse. */
  rechercher(c: { carte: string; compte: string; reference: string; du: string | undefined; au: string | undefined }): Observable<ResultatPorteur> {
    return this.http.post<ResultatPorteur>('/api/reclamations/recherche', c);
  }

  referentiels(): Observable<Referentiels> {
    return this.http.get<Referentiels>('/api/reclamations/referentiels');
  }

  lister(statut: string, recherche: string): Observable<ResumeReclamation[]> {
    return this.http.get<ResumeReclamation[]>('/api/reclamations', { params: { statut, recherche } });
  }

  detail(id: number): Observable<Reclamation> {
    return this.http.get<Reclamation>(`/api/reclamations/${id}`);
  }

  ouvrir(s: SaisieReclamation): Observable<Reclamation> {
    return this.http.post<Reclamation>('/api/reclamations', s);
  }

  agir(id: number, action: string, commentaire: string | null): Observable<Reclamation> {
    return this.http.post<Reclamation>(`/api/reclamations/${id}/actions`, { action, commentaire });
  }
}
