import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { AuthService, UtilisateurConnecte } from './core/auth.service';

const utilisateur = (menus: string[], doitChangerMotDePasse = false): UtilisateurConnecte => ({
  login: 'agent', nom: 'Agent Test', agence: null, profil: 'P', libelleProfil: 'Profil test', menus, doitChangerMotDePasse,
});

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  async function rendre(u: UtilisateurConnecte | null) {
    const fixture = TestBed.createComponent(App);
    TestBed.inject(AuthService).charger().subscribe();
    const req = http.expectOne('/api/auth/moi');
    if (u) req.flush(u); else req.flush(null, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('n\'affiche pas le menu sans session', async () => {
    const el = await rendre(null);
    expect(el.querySelector('mat-sidenav')).toBeNull();
  });

  it('n\'affiche que les menus autorisés par le profil', async () => {
    const el = await rendre(utilisateur(['ACCUEIL', 'EXPLORATION', 'EXPLORATION_LIS']));
    const liens = Array.from(el.querySelectorAll('mat-nav-list a [matListItemTitle]')).map((a) => a.textContent?.trim());
    expect(liens).toEqual(['Accueil', 'Données LIS']);
    expect(el.textContent).toContain('Agent Test');
  });

  it('masque le menu tant que le mot de passe doit être changé', async () => {
    const el = await rendre(utilisateur(['ACCUEIL'], true));
    expect(el.querySelector('mat-sidenav')).toBeNull();
  });
});
