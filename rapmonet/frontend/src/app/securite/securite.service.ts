import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResultat } from '../exploration/exploration-lis.service';

export interface Utilisateur {
  login: string;
  nom: string;
  agence: string | null;
  email: string | null;
  profilCode: string;
  libelleProfil: string;
  actif: boolean;
  verrouille: boolean;
  doitChangerMdp: boolean;
  derniereConnexion: string | null;
  creeLe: string;
  deuxFacteurs: boolean;
  mdpModifieLe: string;
}
export interface PolitiqueSecurite {
  longueurMin: number; majuscule: boolean; minuscule: boolean; chiffre: boolean; special: boolean;
  expirationJours: number; historique: number; echecsMax: number; deuxFacteursObligatoire: boolean;
}
export interface Menu { code: string; libelle: string; parentCode: string | null; rang: number }
export interface Profil { code: string; libelle: string; description: string | null; systeme: boolean; menus: string[]; nbUtilisateurs: number }
export interface EvenementAudit {
  id: number; horodatage: string; login: string | null; action: string; objet: string | null;
  detail: string | null; adresseIp: string | null; succes: boolean;
}
export interface CritereAudit { du?: string; au?: string; login?: string; action?: string }

@Injectable({ providedIn: 'root' })
export class SecuriteService {
  private readonly http = inject(HttpClient);

  utilisateurs(): Observable<Utilisateur[]> {
    return this.http.get<Utilisateur[]>('/api/securite/utilisateurs');
  }

  creerUtilisateur(u: { login: string; nom: string; agence: string; email: string; profilCode: string; motDePasseTemporaire: string }) {
    return this.http.post<Utilisateur>('/api/securite/utilisateurs', u);
  }

  modifierUtilisateur(login: string, u: { nom: string; agence: string | null; email: string | null; profilCode: string; actif: boolean }) {
    return this.http.put<Utilisateur>(`/api/securite/utilisateurs/${encodeURIComponent(login)}`, u);
  }

  deverrouiller(login: string) {
    return this.http.post<Utilisateur>(`/api/securite/utilisateurs/${encodeURIComponent(login)}/deverrouiller`, {});
  }

  reinitialiser(login: string, nouveau: string, doitChanger: boolean) {
    return this.http.post<Utilisateur>(`/api/securite/utilisateurs/${encodeURIComponent(login)}/mot-de-passe`, { nouveau, doitChanger });
  }

  reinitialiserDeuxFacteurs(login: string) {
    return this.http.post<Utilisateur>(`/api/securite/utilisateurs/${encodeURIComponent(login)}/deux-facteurs/reinitialisation`, {});
  }

  politique(): Observable<PolitiqueSecurite> {
    return this.http.get<PolitiqueSecurite>('/api/securite/politique');
  }

  modifierPolitique(p: PolitiqueSecurite): Observable<PolitiqueSecurite> {
    return this.http.put<PolitiqueSecurite>('/api/securite/politique', p);
  }

  menus(): Observable<Menu[]> {
    return this.http.get<Menu[]>('/api/securite/menus');
  }

  profils(): Observable<Profil[]> {
    return this.http.get<Profil[]>('/api/securite/profils');
  }

  creerProfil(p: { code: string; libelle: string; description: string | null; menus: string[] }) {
    return this.http.post<Profil>('/api/securite/profils', p);
  }

  modifierProfil(code: string, p: { code: string; libelle: string; description: string | null; menus: string[] }) {
    return this.http.put<Profil>(`/api/securite/profils/${code}`, p);
  }

  supprimerProfil(code: string) {
    return this.http.delete<void>(`/api/securite/profils/${code}`);
  }

  audit(critere: CritereAudit, page: number, taille: number): Observable<PageResultat<EvenementAudit>> {
    let params = new HttpParams().set('page', page).set('taille', taille);
    for (const [k, v] of Object.entries(critere)) if (v) params = params.set(k, v);
    return this.http.get<PageResultat<EvenementAudit>>('/api/securite/audit', { params });
  }

  actionsAudit(): Observable<string[]> {
    return this.http.get<string[]>('/api/securite/audit/actions');
  }
}
