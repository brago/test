import { Component, DestroyRef, inject, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  ConfigurationBatch, EjournalService, EtatBatch, ExecutionBatch, LIBELLES_RESULTAT_BATCH, LIBELLES_STATUT_BATCH,
} from '../core/ejournal';
import { messageErreur } from '../core/erreurs';

/** Intégration automatique quotidienne des journaux déposés dans un dossier. */
@Component({
  selector: 'app-batch-ejournal',
  imports: [DatePipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressBarModule, MatSlideToggleModule, MatTooltipModule],
  templateUrl: './batch-ejournal.html',
  styleUrl: './batch-ejournal.scss',
})
export class BatchEjournal {
  private readonly service = inject(EjournalService);

  /** Émis à la fin d'une exécution, pour rafraîchir la liste des journaux intégrés. */
  readonly termine = output<void>();

  protected readonly etat = signal<EtatBatch | null>(null);
  protected readonly edition = signal<ConfigurationBatch | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly ouverte = signal<number | null>(null);
  protected readonly statuts = LIBELLES_STATUT_BATCH;
  protected readonly resultats = LIBELLES_RESULTAT_BATCH;
  private suivi: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.recharger();
    inject(DestroyRef).onDestroy(() => this.arreterSuivi());
  }

  protected modifier(): void {
    const c = this.etat()?.configuration;
    if (c) this.edition.set({ ...c });
  }

  protected enregistrer(): void {
    const c = this.edition();
    if (!c) return;
    this.erreur.set(null);
    this.service.modifierBatch(c).subscribe({
      next: () => {
        this.edition.set(null);
        this.recharger();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Paramétrage refusé.')),
    });
  }

  protected lancer(): void {
    this.erreur.set(null);
    this.service.lancerBatch().subscribe({
      next: (r) => {
        this.ouverte.set(r.id);
        this.recharger();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Lancement impossible.')),
    });
  }

  protected basculer(ex: ExecutionBatch): void {
    this.ouverte.set(this.ouverte() === ex.id ? null : ex.id);
  }

  protected classeStatut(statut: string): string {
    return statut === 'TERMINE' ? 'ok' : statut === 'EN_COURS' ? '' : 'ko';
  }

  private recharger(): void {
    this.service.etatBatch().subscribe({
      next: (e) => {
        const etaitEnCours = this.etat()?.enCours;
        this.etat.set(e);
        if (e.enCours) {
          this.demarrerSuivi();
        } else {
          this.arreterSuivi();
          if (etaitEnCours) this.termine.emit();
        }
      },
      error: (e) => this.erreur.set(messageErreur(e, 'État du traitement automatique indisponible.')),
    });
  }

  /** Pendant une exécution, l'état est relu toutes les 2 secondes. */
  private demarrerSuivi(): void {
    this.suivi ??= setInterval(() => this.recharger(), 2000);
  }

  private arreterSuivi(): void {
    if (this.suivi) clearInterval(this.suivi);
    this.suivi = null;
  }
}
