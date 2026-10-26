import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Accueil } from './accueil';

describe('Accueil', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('affiche la version et la base quand le serveur répond', async () => {
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    http.expectOne('/api/systeme/info').flush({
      application: 'RAPMONET',
      version: '0.1.0',
      baseDeDonnees: { produit: 'PostgreSQL', version: '16', schemaVersion: '1' },
    });
    await fixture.whenStable();
    fixture.detectChanges();
    const texte = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texte).toContain('Serveur connecté');
    expect(texte).toContain('PostgreSQL 16');
  });

  it('affiche les alertes à traiter avec leur lien', async () => {
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    http.expectOne('/api/alertes').flush([
      { code: 'CHARGEBACK_RECU_ECHEANCE', niveau: 'CRITIQUE', titre: 'Chargebacks reçus à traiter avant échéance',
        detail: 'Échéance dans 7 jours ou moins', nombre: 2, montant: 150000, menu: 'CHARGEBACK_ACQUISITION',
        lien: '/chargeback/acquisition' },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Chargebacks reçus à traiter avant échéance');
    expect(el.textContent).toContain('150');
    expect(el.querySelector('a.titre')?.getAttribute('href')).toBe('/chargeback/acquisition');
  });

  it('signale un serveur injoignable', async () => {
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    http.expectOne('/api/systeme/info').error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Serveur injoignable');
  });
});
