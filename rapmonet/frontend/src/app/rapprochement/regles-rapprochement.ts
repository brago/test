import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { messageErreur } from '../core/erreurs';
import { dateIso } from '../core/format';
import {
  ChampReference, ModificationRegles, RapprochementService, ReglesGabLis, SimulationRegles, SyntheseRapprochement,
} from './rapprochement.service';

/** Règles du rapprochement GAB ↔ LIS : modification, simulation sur une période, historique motivé. */
@Component({
  selector: 'app-regles-rapprochement',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatDatepickerModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSlideToggleModule],
  templateUrl: './regles-rapprochement.html',
  styleUrl: './rapprochement.scss',
})
export class ReglesRapprochement {
  private readonly service = inject(RapprochementService);

  protected readonly champs: { code: ChampReference; libelle: string }[] = [
    { code: 'AUTORISATION', libelle: 'Code d\'autorisation' },
    { code: 'BORDEREAU', libelle: 'Numéro de bordereau' },
    { code: 'RRN', libelle: 'Fin du RRN' },
  ];
  protected readonly lignesSynthese: { libelle: string; nb: keyof SyntheseRapprochement; montant: keyof SyntheseRapprochement }[] = [
    { libelle: 'Rapprochés', nb: 'rapproches', montant: 'montantRapproche' },
    { libelle: 'Retraits non remontés par le GIM', nb: 'nonRemontes', montant: 'montantNonRemonte' },
    { libelle: 'Avis LIS sans retrait au journal', nb: 'avisSansRetrait', montant: 'montantAvisSansRetrait' },
    { libelle: 'En attente des LIS', nb: 'enAttente', montant: 'montantEnAttente' },
    { libelle: 'Écarts à traiter', nb: 'ecartsATraiter', montant: 'ecartsATraiter' },
  ];

  protected readonly enVigueur = signal<ReglesGabLis | null>(null);
  protected readonly edition = signal<ReglesGabLis | null>(null);
  protected readonly historique = signal<ModificationRegles[]>([]);
  protected readonly simulation = signal<SimulationRegles | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly enCours = signal(false);

  protected du: Date = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1);
  protected au: Date = new Date();
  protected terminal = '';
  protected motif = '';

  constructor() {
    this.charger();
  }

  private charger(): void {
    this.service.regles().subscribe({
      next: (r) => {
        this.enVigueur.set(r);
        this.edition.set({ ...r, champsReference: [...r.champsReference] });
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.service.historiqueRegles().subscribe((h) => this.historique.set(h));
  }

  protected coche(r: ReglesGabLis, c: ChampReference): boolean {
    return r.champsReference.includes(c);
  }

  protected basculer(r: ReglesGabLis, c: ChampReference, actif: boolean): void {
    r.champsReference = actif ? [...r.champsReference.filter((x) => x !== c), c] : r.champsReference.filter((x) => x !== c);
    this.simulation.set(null);
  }

  protected modifie(): boolean {
    const a = this.enVigueur(), b = this.edition();
    if (!a || !b) return false;
    const n = (r: ReglesGabLis) => JSON.stringify({ ...r, champsReference: [...r.champsReference].sort() });
    return n(a) !== n(b);
  }

  protected simuler(): void {
    const r = this.edition();
    const du = dateIso(this.du), au = dateIso(this.au);
    if (!r || !du || !au) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.service.simuler(r, du, au, this.terminal.trim()).subscribe({
      next: (s) => { this.simulation.set(s); this.enCours.set(false); },
      error: (e) => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  protected enregistrer(): void {
    const r = this.edition();
    if (!r) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.message.set(null);
    this.service.modifierRegles(r, this.motif).subscribe({
      next: () => {
        this.enCours.set(false);
        this.motif = '';
        this.simulation.set(null);
        this.message.set('Règles enregistrées : elles s\'appliquent dès le prochain rapprochement.');
        this.charger();
      },
      error: (e) => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  protected retablir(): void {
    const r = this.enVigueur();
    if (r) this.edition.set({ ...r, champsReference: [...r.champsReference] });
    this.simulation.set(null);
  }

  protected difference(d: number): string {
    return d === 0 ? '=' : (d > 0 ? '+' : '−') + Math.abs(d).toLocaleString('fr-FR');
  }

  protected valeur(s: SyntheseRapprochement, cle: keyof SyntheseRapprochement): number {
    return s[cle] as number;
  }
}
