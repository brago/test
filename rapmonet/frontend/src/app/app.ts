import { Component, computed, inject } from '@angular/core';
import { IsActiveMatchOptions, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MENU, EntreeMenu } from './core/menu';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule, MatButtonModule, MatMenuModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);

  /** Lien actif seulement sur son chemin exact (« /exploration » ≠ « /exploration/vss »), paramètres ignorés. */
  protected readonly lienExact: IsActiveMatchOptions = {
    paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored',
  };

  /** Menu limité aux entrées autorisées par le profil. */
  protected readonly menu = computed<EntreeMenu[]>(() => {
    const u = this.auth.utilisateur();
    if (!u || u.doitChangerMotDePasse) return [];
    const filtrer = (entrees: EntreeMenu[]): EntreeMenu[] =>
      entrees
        .filter((e) => u.menus.includes(e.code))
        .map((e) => (e.enfants ? { ...e, enfants: filtrer(e.enfants) } : e))
        .filter((e) => !e.enfants || e.enfants.length > 0);
    return filtrer(MENU);
  });

  protected deconnecter(): void {
    this.auth.deconnecter().subscribe();
  }
}
