import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';
import { FichierVss, ResultatVss, VssService, afficherMontantSigne } from '../core/vss';
import { AnomalieLis } from './lis.service';

/** Chargement des fichiers de rapports VSS (VX / EP-747) et historique. */
@Component({
  selector: 'app-chargement-vss',
  imports: [DatePipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTableModule, MatTooltipModule],
  templateUrl: './chargement-vss.html',
  styleUrl: './chargement-lis.scss',
})
export class ChargementVss {
  private readonly service = inject(VssService);

  protected readonly fichier = signal<File | null>(null);
  protected readonly enCours = signal(false);
  protected readonly resultat = signal<ResultatVss | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly fichiers = signal<FichierVss[]>([]);
  protected readonly anomaliesAffichees = signal<{ fichier: FichierVss; anomalies: AnomalieLis[] } | null>(null);
  protected readonly montant = afficherMontantSigne;
  protected readonly colonnes = ['date', 'nom', 'sre', 'net', 'nbTransactions', 'compensation', 'nbRapports', 'nbAvertissements', 'integre', 'actions'];

  constructor() {
    this.recharger();
  }

  protected choisir(e: Event): void {
    const input = e.target as HTMLInputElement;
    this.fichier.set(input.files?.[0] ?? null);
    input.value = '';
  }

  protected integrer(): void {
    const f = this.fichier();
    if (!f) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.resultat.set(null);
    this.service.integrer(f).subscribe({
      next: (r) => {
        this.resultat.set(r);
        this.enCours.set(false);
        if (r.integre) {
          this.fichier.set(null);
          this.recharger();
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e, 'Erreur lors de l\'intégration.'));
        this.enCours.set(false);
      },
    });
  }

  protected voirAnomalies(f: FichierVss): void {
    this.service.anomalies(f.id).subscribe((anomalies) => this.anomaliesAffichees.set({ fichier: f, anomalies }));
  }

  protected annuler(f: FichierVss): void {
    if (!confirm(`Annuler l'intégration de ${f.nom} (${f.dateTraitement}) ? Ses rapports seront supprimés.`)) return;
    this.service.annuler(f.id).subscribe(() => {
      this.anomaliesAffichees.set(null);
      this.recharger();
    });
  }

  private recharger(): void {
    this.service.fichiers().subscribe((f) => this.fichiers.set(f));
  }
}
