import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { messageErreur } from '../core/erreurs';
import { Telechargement } from '../core/telechargement';
import { GraphiqueBarres, PointGraphique } from './graphique-barres';
import { Axe, Indicateur, Kpi, ReportingService } from './reporting.service';

const MOIS_COURTS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

function moisIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** Indicateurs de performance en volume (GAB, TPE, cartes) : graphiques et tableau croisé par mois. */
@Component({
  selector: 'app-kpi',
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, GraphiqueBarres],
  templateUrl: './kpi.html',
  styleUrl: './reporting.scss',
})
export class KpiComponent {
  private readonly service = inject(ReportingService);
  private readonly telechargement = inject(Telechargement);

  protected readonly indicateurs = signal<Indicateur[]>([]);
  protected readonly axes: { axe: Axe; libelle: string }[] = [
    { axe: 'GAB', libelle: 'GAB' }, { axe: 'TPE', libelle: 'TPE' }, { axe: 'CARTE', libelle: 'Cartes de la banque' }];
  protected indicateur = 'GAB_RETRAITS_JOURNAUX';
  protected du = moisIso(new Date(new Date().getFullYear(), new Date().getMonth() - 11, 1));
  protected au = moisIso(new Date());
  protected readonly mesure = signal<'MONTANT' | 'NOMBRE'>('MONTANT');
  protected readonly kpi = signal<Kpi | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly chargement = signal(false);

  protected readonly description = computed(() => this.indicateurs().find((i) => i.code === this.kpi()?.indicateur)?.description);

  protected readonly parMois = computed<PointGraphique[]>(() => {
    const k = this.kpi();
    if (!k) return [];
    const valeurs = this.mesure() === 'MONTANT' ? k.totauxMontant : k.totauxNombre;
    return k.mois.map((m, i) => ({ libelle: this.libelleMois(m), valeur: valeurs[i],
      detail: this.mesure() === 'MONTANT' ? `${k.totauxNombre[i]} opération(s)` : undefined }));
  });

  protected readonly classement = computed<PointGraphique[]>(() => {
    const k = this.kpi();
    if (!k) return [];
    const montant = this.mesure() === 'MONTANT';
    return [...k.lignes].sort((a, b) => montant ? b.totalMontant - a.totalMontant : b.totalNombre - a.totalNombre)
      .slice(0, 10)
      .map((l) => ({ libelle: l.libelle ? `${l.entite} – ${l.libelle}` : l.entite,
        valeur: montant ? l.totalMontant : l.totalNombre, detail: montant ? `${l.totalNombre} opération(s)` : undefined }));
  });

  constructor() {
    this.service.indicateurs().subscribe((i) => this.indicateurs.set(i));
    this.afficher();
  }

  protected indicateursDe(axe: Axe): Indicateur[] {
    return this.indicateurs().filter((i) => i.axe === axe);
  }

  protected afficher(): void {
    this.erreur.set(null);
    this.chargement.set(true);
    this.service.kpi(this.indicateur, this.du, this.au).subscribe({
      next: (k) => {
        this.kpi.set(k);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.telechargement.telecharger('/api/reporting/kpi/export',
      { format, indicateur: this.indicateur, du: this.du, au: this.au, mesure: this.mesure() })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  protected libelleMois(m: string): string {
    const [a, mm] = m.split('-');
    return `${MOIS_COURTS[+mm - 1]} ${a.substring(2)}`;
  }

  protected valeurs(l: { nombres: number[]; montants: number[] }): number[] {
    return this.mesure() === 'MONTANT' ? l.montants : l.nombres;
  }

  protected entite(axe: Axe): string {
    return axe === 'CARTE' ? 'BIN' : axe === 'TPE' ? 'Terminal' : 'GAB';
  }
}
