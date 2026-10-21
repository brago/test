import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Accueil } from './accueil';

describe('Accueil', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [provideHttpClient(), provideHttpClientTesting()],
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

  it('signale un serveur injoignable', async () => {
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    http.expectOne('/api/systeme/info').error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Serveur injoignable');
  });
});
