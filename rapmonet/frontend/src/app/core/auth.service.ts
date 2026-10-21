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
}

/** Session de l'utilisateur (cookie de session côté serveur). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly etat = signal<UtilisateurConnecte | null | undefined>(undefined);

  /** undefined : pas encore vérifié ; null : non connecté. */
  readonly utilisateur = this.etat.asReadonly();
  readonly connecte = computed(() => !!this.etat() && !this.etat()!.doitChangerMotDePasse);

  /** Vérifie la session auprès du serveur (une seule fois, sauf si forcer). */
  charger(forcer = false): Observable<UtilisateurConnecte | null> {
    if (!forcer && this.etat() !== undefined) return of(this.etat()!);
    return this.http.get<UtilisateurConnecte>('/api/auth/moi').pipe(
      catchError(() => of(null)),
      tap((u) => this.etat.set(u)),
    );
  }

  connecter(login: string, motDePasse: string): Observable<UtilisateurConnecte> {
    return this.http.post<UtilisateurConnecte>('/api/auth/connexion', { login, motDePasse }).pipe(tap((u) => this.etat.set(u)));
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
    return !!u && !u.doitChangerMotDePasse && u.menus.includes(menu);
  }
}
