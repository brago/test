import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';

interface EtatCategorie {
  code: string; libelle: string; description: string; dureeMinimaleMois: number; dureeParDefautMois: number; dureeMois: number;
  limite: string; total: number; plusAncienne: string | null; aPurger: number;
}
interface LigneExecution { code: string; libelle: string; dureeMois: number; limite: string; supprimes: number; erreur: string | null; }
interface Execution { id: number; declencheur: string; lancePar: string | null; motif: string | null; debut: string; fin: string | null; statut: string; detail: LigneExecution[]; }
interface EtatConservation { categories: EtatCategorie[]; planification: { planifiee: boolean; jour: number }; dernieresExecutions: Execution[]; }

const API = '/api/parametrage/conservation';

/** Durées de conservation par catégorie, aperçu, purge confirmée et purge automatique mensuelle. */
@Component({
  selector: 'app-conservation',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSlideToggleModule, MatTooltipModule],
  templateUrl: './conservation.html',
  styleUrl: './format-lis.scss',
})
export class Conservation {
  private readonly http = inject(HttpClient);

  protected readonly etat = signal<EtatConservation | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly duree = signal<{ code: string; mois: number; motif: string } | null>(null);
  protected readonly planif = signal<{ planifiee: boolean; jour: number; motif: string } | null>(null);
  protected readonly purge = signal<{ motif: string; confirmation: string } | null>(null);
  protected readonly ouverte = signal<number | null>(null);

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.http.get<EtatConservation>(API).subscribe({
      next: (e) => this.etat.set(e), error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected totalAPurger(): number {
    return (this.etat()?.categories ?? []).reduce((s, c) => s + c.aPurger, 0);
  }

  protected annees(mois: number): string {
    return mois % 12 === 0 ? `${mois / 12} an${mois > 12 ? 's' : ''}` : `${mois} mois`;
  }

  protected enregistrerDuree(): void {
    const d = this.duree();
    if (!d) return;
    this.http.put<void>(`${API}/categories/${d.code}`, { mois: Number(d.mois), motif: d.motif }).subscribe({
      next: () => { this.duree.set(null); this.terminer('Durée de conservation enregistrée.'); },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected modifierPlanification(): void {
    const p = this.etat()?.planification;
    if (p) this.planif.set({ ...p, motif: '' });
  }

  protected enregistrerPlanification(): void {
    const p = this.planif();
    if (!p) return;
    this.http.put(`${API}/planification`, { planifiee: p.planifiee, jour: Number(p.jour), motif: p.motif }).subscribe({
      next: () => { this.planif.set(null); this.terminer('Purge automatique enregistrée.'); },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected purger(): void {
    const p = this.purge();
    if (!p) return;
    this.enCours.set(true);
    this.http.post<Execution>(`${API}/purges`, p).subscribe({
      next: (x) => {
        this.enCours.set(false);
        this.purge.set(null);
        this.ouverte.set(x.id);
        this.terminer(`Purge terminée : ${x.detail.reduce((s, l) => s + l.supprimes, 0).toLocaleString('fr-FR')} élément(s) supprimé(s).`);
      },
      error: (e) => { this.enCours.set(false); this.erreur.set(messageErreur(e)); },
    });
  }

  protected totalSupprime(x: Execution): number {
    return x.detail.reduce((s, l) => s + l.supprimes, 0);
  }

  private terminer(m: string): void {
    this.erreur.set(null);
    this.message.set(m);
    this.charger();
  }
}
