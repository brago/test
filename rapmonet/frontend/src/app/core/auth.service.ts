import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, map, of, switchMap, tap } from 'rxjs';

export interface UtilisateurConnecte {
  login: string;
  nom: string;
  agence: string | null;
  profil: string;
  libelleProfil: string;
  menus: string[];
  doitChangerMotDePasse: boolean;
  deuxFacteurs: boolean;
  doitActiverDeuxFacteurs: boolean;
  expirationMotDePasse: string | null;
}

/** Réponse de la connexion quand le code de double authentification est attendu. */
export interface CodeRequis { codeRequis: true }
export interface RegleMotDePasse { libelle: string; motif: string }
export interface Enrolement { secret: string; uri: string; qrSvg: string }

/** Session ouverte et complète : mot de passe à jour et double authentification activée si elle est exigée. */
export function sessionComplete(u: UtilisateurConnecte | null | undefined): u is UtilisateurConnecte {
  return !!u && !u.doitChangerMotDePasse && !u.doitActiverDeuxFacteurs;
}

/** Écran à afficher avant tout autre quand la session est limitée. */
export function etapeRequise(u: UtilisateurConnecte): string | null {
  if (u.doitChangerMotDePasse) return '/mot-de-passe';
  if (u.doitActiverDeuxFacteurs) return '/deux-facteurs';
  return null;
}

/** Session de l'utilisateur (cookie de session côté serveur). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly etat = signal<UtilisateurConnecte | null | undefined>(undefined);

  /** undefined : pas encore vérifié ; null : non connecté. */
  readonly utilisateur = this.etat.asReadonly();
  readonly connecte = computed(() => sessionComplete(this.etat()));

  /** Vérifie la session auprès du serveur (une seule fois, sauf si forcer). */
  charger(forcer = false): Observable<UtilisateurConnecte | null> {
    if (!forcer && this.etat() !== undefined) return of(this.etat()!);
    return this.http.get<UtilisateurConnecte>('/api/auth/moi').pipe(
      catchError(() => of(null)),
      tap((u) => this.etat.set(u)),
    );
  }

  /** Renvoie l'utilisateur, ou codeRequis si la double authentification est activée sur le compte. */
  connecter(login: string, motDePasse: string): Observable<UtilisateurConnecte | CodeRequis> {
    return this.http.post<UtilisateurConnecte | CodeRequis>('/api/auth/connexion', { login, motDePasse }).pipe(
      tap((r) => {
        if (!('codeRequis' in r)) this.etat.set(r);
      }),
    );
  }

  verifierCode(code: string): Observable<UtilisateurConnecte> {
    return this.http.post<UtilisateurConnecte>('/api/auth/code', { code }).pipe(tap((u) => this.etat.set(u)));
  }

  reglesMotDePasse(): Observable<RegleMotDePasse[]> {
    return this.http.get<RegleMotDePasse[]>('/api/auth/politique-mot-de-passe');
  }

  enrolement(): Observable<Enrolement> {
    return this.http.post<Enrolement>('/api/auth/deux-facteurs/enrolement', {});
  }

  activerDeuxFacteurs(code: string): Observable<UtilisateurConnecte> {
    return this.http.post<UtilisateurConnecte>('/api/auth/deux-facteurs/activation', { code }).pipe(tap((u) => this.etat.set(u)));
  }

  desactiverDeuxFacteurs(motDePasse: string): Observable<UtilisateurConnecte> {
    return this.http.post<UtilisateurConnecte>('/api/auth/deux-facteurs/desactivation', { motDePasse }).pipe(tap((u) => this.etat.set(u)));
  }

  changerMotDePasse(actuel: string, nouveau: string): Observable<UtilisateurConnecte> {
    return this.http.post<UtilisateurConnecte>('/api/auth/mot-de-passe', { actuel, nouveau }).pipe(tap((u) => this.etat.set(u)));
  }

  /**
   * Ferme la session puis affiche l'écran de connexion. L'état local n'est effacé qu'après
   * la navigation : sinon l'écran courant serait reconstruit hors session et relancerait ses requêtes.
   */
  deconnecter(): Observable<void> {
    return this.http.post<void>('/api/auth/deconnexion', {}).pipe(
      catchError(() => of(undefined)),
      switchMap(() => from(this.router.navigate(['/connexion']))),
      map(() => this.etat.set(null)),
    );
  }

  /** Session perdue (expirée ou compte désactivé). */
  oublier(): void {
    this.etat.set(null);
  }

  peut(menu: string): boolean {
    const u = this.etat();
    return sessionComplete(u) && u.menus.includes(menu);
  }
}
