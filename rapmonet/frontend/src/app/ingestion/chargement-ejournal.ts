import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EjournalService, FichierJournal, ResultatJournal } from '../core/ejournal';
import { messageErreur } from '../core/erreurs';
import { AnomalieLis } from './lis.service';

/** Chargement des journaux électroniques des GAB (plusieurs fichiers à la fois). */
@Component({
  selector: 'app-chargement-ejournal',
  imports: [DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTableModule, MatTooltipModule],
  templateUrl: './chargement-ejournal.html',
  styleUrl: './chargement-lis.scss',
})
export class ChargementEjournal {
  private readonly service = inject(EjournalService);

  protected readonly selection = signal<File[]>([]);
  protected readonly enCours = signal(false);
  protected readonly resultats = signal<ResultatJournal[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly fichiers = signal<FichierJournal[]>([]);
  protected readonly anomaliesAffichees = signal<{ fichier: FichierJournal; anomalies: AnomalieLis[] } | null>(null);
  protected readonly colonnes = ['terminal', 'periode', 'nom', 'nbSessions', 'nbOperations', 'nbDoublons', 'retraits', 'montant', 'nbEvenements', 'nbAvertissements', 'integre', 'actions'];

  constructor() {
    this.recharger();
  }

  protected choisir(e: Event): void {
    const input = e.target as HTMLInputElement;
    this.selection.set(Array.from(input.files ?? []));
    input.value = '';
  }

  protected integrer(): void {
    if (!this.selection().length) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.resultats.set([]);
    this.service.integrer(this.selection()).subscribe({
      next: (r) => {
        this.resultats.set(r);
        this.enCours.set(false);
        this.selection.set([]);
        this.recharger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e, 'Erreur lors de l\'intégration.'));
        this.enCours.set(false);
      },
    });
  }

  protected voirAnomalies(f: FichierJournal): void {
    this.service.anomalies(f.id).subscribe((anomalies) => this.anomaliesAffichees.set({ fichier: f, anomalies }));
  }

  protected annuler(f: FichierJournal): void {
    if (!confirm(`Annuler l'intégration de ${f.nom} ? Ses opérations seront supprimées.`)) return;
    this.service.annuler(f.id).subscribe(() => {
      this.anomaliesAffichees.set(null);
      this.recharger();
    });
  }

  private recharger(): void {
    this.service.fichiers().subscribe((f) => this.fichiers.set(f));
  }
}
