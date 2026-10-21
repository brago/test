import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AnomalieLis, FichierLis, LisService, RapportIntegration } from './lis.service';

/** Chargement des fichiers LIS (SW et PR) d'une journée et historique des intégrations. */
@Component({
  selector: 'app-chargement-lis',
  imports: [
    DatePipe, DecimalPipe, KeyValuePipe,
    MatButtonModule, MatCardModule, MatIconModule, MatTableModule, MatChipsModule, MatProgressBarModule, MatTooltipModule,
  ],
  templateUrl: './chargement-lis.html',
  styleUrl: './chargement-lis.scss',
})
export class ChargementLis {
  private readonly service = inject(LisService);

  protected readonly sw = signal<File | null>(null);
  protected readonly pr = signal<File | null>(null);
  protected readonly enCours = signal(false);
  protected readonly rapport = signal<RapportIntegration | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly fichiers = signal<FichierLis[]>([]);
  protected readonly anomaliesAffichees = signal<{ fichier: FichierLis; anomalies: AnomalieLis[] } | null>(null);

  protected readonly colonnesHistorique = ['journee', 'typeLis', 'nom', 'nbEnregistrements', 'nbTransactions', 'repartition', 'nbAvertissements', 'integreLe', 'actions'];
  protected readonly colonnesAnomalies = ['gravite', 'ligne', 'message'];

  constructor() {
    this.recharger();
  }

  protected choisir(type: 'sw' | 'pr', evenement: Event): void {
    const input = evenement.target as HTMLInputElement;
    const fichier = input.files?.[0] ?? null;
    (type === 'sw' ? this.sw : this.pr).set(fichier);
    input.value = '';
  }

  protected integrer(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.rapport.set(null);
    this.service.integrer(this.sw(), this.pr()).subscribe({
      next: (r) => {
        this.rapport.set(r);
        this.enCours.set(false);
        if (r.integre) {
          this.sw.set(null);
          this.pr.set(null);
          this.recharger();
        }
      },
      error: (e) => {
        this.erreur.set(e?.error?.detail ?? 'Erreur lors de l\'intégration.');
        this.enCours.set(false);
      },
    });
  }

  protected voirAnomalies(fichier: FichierLis): void {
    this.service.anomalies(fichier.id).subscribe((anomalies) => this.anomaliesAffichees.set({ fichier, anomalies }));
  }

  protected annuler(fichier: FichierLis): void {
    if (!confirm(`Annuler l'intégration de ${fichier.nom} ? Toutes ses transactions seront supprimées.`)) return;
    this.service.annuler(fichier.id).subscribe(() => {
      this.anomaliesAffichees.set(null);
      this.recharger();
    });
  }

  private recharger(): void {
    this.service.lister().subscribe((f) => this.fichiers.set(f));
  }
}
