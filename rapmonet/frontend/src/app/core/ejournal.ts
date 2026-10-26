import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnomalieLis } from '../ingestion/lis.service';
import { PageResultat } from '../exploration/exploration-lis.service';

export interface ResultatJournal {
  nom: string; integre: boolean; id: number | null; terminal: string | null; dateDebut: string | null; dateFin: string | null;
  nbSessions: number; nbOperations: number; nbDoublons: number; nbRetraitsApprouves: number; montantRetraits: number;
  nbEvenements: number; anomalies: AnomalieLis[];
}
export interface FichierJournal {
  id: number; nom: string; terminal: string; dateDebut: string | null; dateFin: string | null; nbSessions: number;
  nbOperations: number; nbDoublons: number; nbRetraitsApprouves: number; montantRetraits: number; nbEvenements: number;
  nbAvertissements: number; integreLe: string; integrePar: string | null;
}
export type StatutOperation = 'DISTRIBUE' | 'APPROUVE' | 'NON_PRIS' | 'REFUSE' | 'INCONNU';
export interface OperationGab {
  id: number; sessionId: number; terminal: string; type: 'RETRAIT' | 'SOLDE'; statut: StatutOperation; dateHeure: string | null;
  carte: string | null; montant: number | null; devise: string | null; codeReponse: string | null; libelleReponse: string | null;
  compte: string | null; reference: string | null; billetsPresentes: number[] | null; billetsPris: boolean; incidents: string[];
  montantBillets: number | null; ecartBillets: number | null;
}
export interface SessionGab {
  id: number; nomFichier: string; terminal: string; date: string | null; heureDebut: string | null; carte: string | null;
  billetsPresentes: number[] | null; billetsPris: boolean;
  compteurs: { coupures: number[]; distribues: number[]; rejetes: number[]; restants: number[] } | null;
  incidents: string[]; complete: boolean; texte: string; operations: OperationGab[];
  coupures: number[] | null;
}
export interface EvenementGab {
  id: number; terminal: string; dateHeure: string | null; actions: string[];
  releves: Record<string, number[]>[]; especesAjoutees: Record<string, number>; texte: string;
}
export interface CritereJournal {
  du?: string; au?: string; terminal?: string; type?: string; statut?: string; codeReponse?: string; carte?: string; compte?: string; ecartBillets?: string;
}

export interface ConfigurationBatch {
  actif: boolean; heure: string; repertoireEntree: string; repertoireArchive: string; repertoireRejet: string;
}
export interface FichierTraiteBatch {
  fichier: string; resultat: 'INTEGRE' | 'DEJA_INTEGRE' | 'REJETE'; terminal: string | null; nbOperations: number;
  nbDoublons: number; message: string | null; destination: string;
}
export interface ExecutionBatch {
  id: number; declencheur: 'PLANIFIE' | 'MANUEL'; lancePar: string | null; debut: string; fin: string | null;
  statut: 'EN_COURS' | 'TERMINE' | 'TERMINE_AVEC_REJETS' | 'ERREUR' | 'INTERROMPU';
  nbFichiers: number; nbIntegres: number; nbDejaIntegres: number; nbRejetes: number; nbOperations: number;
  message: string | null; detail: FichierTraiteBatch[];
}
export interface EtatBatch {
  configuration: ConfigurationBatch; prochaineExecution: string | null; enCours: boolean; nbFichiersEnAttente: number;
  dernieresExecutions: ExecutionBatch[];
}
export const API_BATCH_JOURNAUX = '/api/ingestion/ejournal/batch';
export const API_BATCH_LIS = '/api/ingestion/lis/batch';

export const LIBELLES_STATUT_BATCH: Record<string, string> = {
  EN_COURS: 'En cours', TERMINE: 'Terminé', TERMINE_AVEC_REJETS: 'Terminé avec rejets', ERREUR: 'Erreur', INTERROMPU: 'Interrompu',
};
export const LIBELLES_RESULTAT_BATCH: Record<string, string> = {
  INTEGRE: 'Intégré', DEJA_INTEGRE: 'Déjà intégré', REJETE: 'Rejeté',
};

export const LIBELLES_STATUT: Record<string, string> = {
  DISTRIBUE: 'Billets distribués', APPROUVE: 'Approuvée', NON_PRIS: 'Billets non pris', REFUSE: 'Refusée', INCONNU: 'Inconnu',
};
export const LIBELLES_ACTION: Partial<Record<string, string>> = {
  REMISE_A_ZERO_COMPTEURS: 'Arrêté (remise à zéro des compteurs)', AJOUT_ESPECES: 'Ajout d\'espèces',
  RETRAIT_CARTES_CAPTUREES: 'Retrait des cartes capturées',
};

function params(o: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(o)) if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  return p;
}

@Injectable({ providedIn: 'root' })
export class EjournalService {
  private readonly http = inject(HttpClient);

  integrer(fichiers: File[]): Observable<ResultatJournal[]> {
    const data = new FormData();
    fichiers.forEach((f) => data.append('fichiers', f, f.name));
    return this.http.post<ResultatJournal[]>('/api/ingestion/ejournal', data);
  }

  fichiers(): Observable<FichierJournal[]> {
    return this.http.get<FichierJournal[]>('/api/ingestion/ejournal');
  }

  anomalies(id: number): Observable<AnomalieLis[]> {
    return this.http.get<AnomalieLis[]>(`/api/ingestion/ejournal/${id}/anomalies`);
  }

  annuler(id: number): Observable<void> {
    return this.http.delete<void>(`/api/ingestion/ejournal/${id}`);
  }

  operations(c: CritereJournal, page: number, taille: number): Observable<PageResultat<OperationGab>> {
    return this.http.get<PageResultat<OperationGab>>('/api/exploration/ejournal/operations', { params: params({ ...c, page, taille }) });
  }

  session(id: number): Observable<SessionGab> {
    return this.http.get<SessionGab>(`/api/exploration/ejournal/sessions/${id}`);
  }

  evenements(c: { du?: string; au?: string; terminal?: string }): Observable<EvenementGab[]> {
    return this.http.get<EvenementGab[]>('/api/exploration/ejournal/evenements', { params: params(c) });
  }

  /** Traitements automatiques : `api` vaut `/api/ingestion/ejournal/batch` ou `/api/ingestion/lis/batch`. */
  etatBatch(api = API_BATCH_JOURNAUX): Observable<EtatBatch> {
    return this.http.get<EtatBatch>(api);
  }

  modifierBatch(c: ConfigurationBatch, api = API_BATCH_JOURNAUX): Observable<ConfigurationBatch> {
    return this.http.put<ConfigurationBatch>(`${api}/configuration`, c);
  }

  lancerBatch(api = API_BATCH_JOURNAUX): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(`${api}/executions`, null);
  }

  terminaux(): Observable<string[]> {
    return this.http.get<string[]>('/api/exploration/ejournal/terminaux');
  }
}
