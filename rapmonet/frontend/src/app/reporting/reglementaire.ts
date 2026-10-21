import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { messageErreur } from '../core/erreurs';
import { Telechargement } from '../core/telechargement';
import { EtatTrimestriel, LigneTrimestre, ReportingService } from './reporting.service';

/** Base trimestrielle des états réglementaires BCEAO / VISA (émission et acquisition par réseau). */
@Component({
  selector: 'app-reglementaire',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule],
  templateUrl: './reglementaire.html',
  styleUrl: './reporting.scss',
})
export class Reglementaire {
  private readonly service = inject(ReportingService);
  private readonly telechargement = inject(Telechargement);
  private readonly maintenant = new Date();

  protected annee = this.maintenant.getFullYear();
  protected trimestre = Math.floor(this.maintenant.getMonth() / 3) + 1;
  protected readonly annees = Array.from({ length: 6 }, (_, i) => this.maintenant.getFullYear() - i);
  protected readonly etat = signal<EtatTrimestriel | null>(null);
  protected readonly erreur = signal<string | null>(null);

  /** Lignes regroupées par section, pour l'affichage. */
  protected readonly sections = computed(() => {
    const e = this.etat();
    if (!e) return [];
    const m = new Map<string, LigneTrimestre[]>();
    e.lignes.forEach((l) => m.set(l.section, [...(m.get(l.section) ?? []), l]));
    return [...m.entries()].map(([section, lignes]) => ({ section, lignes,
      nombre: lignes.reduce((s, l) => s + l.nombre, 0), montant: lignes.reduce((s, l) => s + l.montant, 0) }));
  });

  constructor() {
    this.afficher();
  }

  protected afficher(): void {
    this.erreur.set(null);
    this.service.trimestre(this.annee, this.trimestre).subscribe({
      next: (e) => this.etat.set(e),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.telechargement.telecharger('/api/reporting/reglementaire/trimestre/export',
      { format, annee: this.annee, trimestre: this.trimestre })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }
}
