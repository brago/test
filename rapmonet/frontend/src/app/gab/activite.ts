import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { GraphiqueBarres, PointGraphique } from '../reporting/graphique-barres';
import { ActiviteGabService, ActiviteGabs, DetailActiviteGab, LigneActiviteGab } from './activite.service';

const ICONES: Record<string, string> = {
  TAUX_REFUS: 'block', INACTIVITE: 'hourglass_empty', CARTES_CAPTUREES: 'credit_card_off', JOURS_SANS_JOURNAL: 'event_busy',
};

/** Activité des GAB : synthèse de la période, GAB signalés, détail jour par jour d'un GAB. */
@Component({
  selector: 'app-activite-gab',
  imports: [DatePipe, DecimalPipe, FormsModule, GraphiqueBarres, MatButtonModule, MatCardModule, MatDatepickerModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatTooltipModule],
  templateUrl: './activite.html',
  styleUrls: ['./gab.scss', './activite.scss'],
})
export class ActiviteGab {
  private readonly service = inject(ActiviteGabService);
  private readonly telechargement = inject(Telechargement);
  protected readonly icones = ICONES;

  protected du: Date = new Date(Date.now() - 6 * 86_400_000);
  protected au: Date = new Date();
  protected terminal = '';
  protected readonly resultat = signal<ActiviteGabs | null>(null);
  protected readonly detail = signal<DetailActiviteGab | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly mesure = signal<'nombre' | 'montant'>('montant');

  protected readonly parJour = computed<PointGraphique[]>(() => {
    const points = this.resultat()?.parJour ?? [];
    // Au-delà de 16 jours, une étiquette sur n pour rester lisible ; la date complète reste dans l'infobulle.
    const pas = Math.max(1, Math.ceil(points.length / 16));
    return points.map((p, i) => {
      const jour = p.libelle.slice(8, 10) + '/' + p.libelle.slice(5, 7);
      return {
        libelle: i % pas === 0 ? jour : '',
        valeur: this.mesure() === 'montant' ? p.montant : p.nombre,
        detail: `${jour} : ${p.nombre} retrait(s) · ${p.montant.toLocaleString('fr-FR')} XOF`,
      };
    });
  });
  protected readonly parHeure = computed<PointGraphique[]>(() =>
    (this.detail()?.parHeure ?? this.resultat()?.parHeure ?? []).map((p) => ({ libelle: p.libelle, valeur: p.nombre })));

  constructor() {
    this.charger();
  }

  protected charger(): void {
    const du = dateIso(this.du), au = dateIso(this.au);
    if (!du || !au) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.detail.set(null);
    this.service.activite(du, au, this.terminal.trim()).subscribe({
      next: (r) => { this.resultat.set(r); this.enCours.set(false); },
      error: (e) => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  protected ouvrir(l: LigneActiviteGab): void {
    const du = dateIso(this.du), au = dateIso(this.au);
    if (!du || !au) return;
    this.service.detail(l.terminal, du, au).subscribe({
      next: (d) => { this.detail.set(d); setTimeout(() => document.getElementById('detail-gab')?.scrollIntoView({ behavior: 'smooth' })); },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected exporter(): void {
    this.telechargement.telecharger('/api/gab/activite/export',
      { format: 'xlsx', du: dateIso(this.du), au: dateIso(this.au), terminal: this.terminal.trim() })
      .subscribe({ error: (e) => this.erreur.set(messageErreur(e)) });
  }

  protected duree(minutes: number): string {
    return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}` : `${minutes} min`;
  }

  protected jjmm(dates: string[]): string {
    return dates.map((d) => d.slice(8, 10) + '/' + d.slice(5, 7)).join(', ');
  }

  protected refusDuJour(r: Record<string, number>): string {
    return Object.entries(r).map(([c, n]) => `${c} × ${n}`).join(', ');
  }
}
