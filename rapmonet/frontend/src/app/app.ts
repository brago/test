import { Component, DestroyRef, computed, effect, inject } from '@angular/core';
import { IsActiveMatchOptions, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MENU, EntreeMenu } from './core/menu';
import { AuthService, sessionComplete } from './core/auth.service';
import { AlertesService } from './core/alertes.service';

/** Les alertes sont recalculées toutes les 5 minutes. */
const PERIODE_ALERTES_MS = 5 * 60 * 1000;

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule, MatButtonModule, MatMenuModule, MatBadgeModule,
    MatTooltipModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly alertes = inject(AlertesService);

  constructor() {
    // Alertes chargées à l'ouverture de session, puis rafraîchies périodiquement.
    effect(() => {
      const u = this.auth.utilisateur();
      if (sessionComplete(u)) this.alertes.rafraichir();
      else this.alertes.vider();
    });
    const minuterie = setInterval(() => {
      const u = this.auth.utilisateur();
      if (sessionComplete(u)) this.alertes.rafraichir();
    }, PERIODE_ALERTES_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  /** Lien actif seulement sur son chemin exact (« /exploration » ≠ « /exploration/vss »), paramètres ignorés. */
  protected readonly lienExact: IsActiveMatchOptions = {
    paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored',
  };

  /** Menu limité aux entrées autorisées par le profil. */
  protected readonly menu = computed<EntreeMenu[]>(() => {
    const u = this.auth.utilisateur();
    if (!sessionComplete(u)) return [];
    const filtrer = (entrees: EntreeMenu[]): EntreeMenu[] =>
      entrees
        .filter((e) => u.menus.includes(e.code))
        .map((e) => (e.enfants ? { ...e, enfants: filtrer(e.enfants) } : e))
        .filter((e) => !e.enfants || e.enfants.length > 0);
    return filtrer(MENU);
  });

  /** Jours restants avant l'expiration du mot de passe, affichés dans les 10 derniers jours. */
  protected readonly joursAvantExpiration = computed(() => {
    const e = this.auth.utilisateur()?.expirationMotDePasse;
    if (!e) return null;
    const jours = Math.ceil((new Date(e + 'T00:00:00').getTime() - Date.now()) / 86_400_000);
    return jours <= 10 ? Math.max(jours, 0) : null;
  });

  protected deconnecter(): void {
    this.auth.deconnecter().subscribe();
  }
}
