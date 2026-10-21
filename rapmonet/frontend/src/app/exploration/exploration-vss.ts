import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatSelectModule } from '@angular/material/select';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { LigneRapportVss, RapportVss, ResumeRapportVss, TypeRapportVss, VssService, afficherValeurVss } from '../core/vss';

/** Consultation des rapports VSS : liste par date, affichage fidèle au rapport Visa, export Excel / PDF. */
@Component({
  selector: 'app-exploration-vss',
  imports: [
    DatePipe, FormsModule, MatButtonModule, MatCardModule, MatDatepickerModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatListModule, MatSelectModule,
  ],
  templateUrl: './exploration-vss.html',
  styleUrl: './exploration-vss.scss',
})
export class ExplorationVss {
  private readonly service = inject(VssService);
  private readonly telechargement = inject(Telechargement);

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected code = '';
  protected readonly types = signal<TypeRapportVss[]>([]);
  protected readonly liste = signal<ResumeRapportVss[]>([]);
  protected readonly rapport = signal<RapportVss | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly valeur = afficherValeurVss;

  /** Rapports regroupés par date de traitement. */
  protected readonly parDate = computed(() => {
    const groupes = new Map<string, ResumeRapportVss[]>();
    for (const r of this.liste()) {
      groupes.set(r.dateTraitement, [...(groupes.get(r.dateTraitement) ?? []), r]);
    }
    return [...groupes.entries()].map(([date, rapports]) => ({ date, rapports }));
  });

  constructor() {
    const date = inject(ActivatedRoute).snapshot.queryParamMap.get('date');
    if (date) {
      this.du = new Date(date + 'T00:00:00');
      this.au = new Date(date + 'T00:00:00');
    }
    this.service.types().subscribe((t) => this.types.set(t));
    this.rechercher();
  }

  protected rechercher(): void {
    this.service.rapports({ du: dateIso(this.du), au: dateIso(this.au), code: this.code }).subscribe((l) => {
      this.liste.set(l);
      const premier = l.find((r) => !r.sansDonnees) ?? l[0];
      if (premier && !l.some((r) => r.id === this.rapport()?.resume.id)) this.ouvrir(premier);
      if (!premier) this.rapport.set(null);
    });
  }

  protected ouvrir(r: ResumeRapportVss): void {
    this.erreur.set(null);
    this.service.rapport(r.id).subscribe((d) => this.rapport.set(d));
  }

  /**
   * Devises du rapport affiché : les montants « CLEARING » sont en devise de compensation, les autres en
   * devise de règlement. La devise de compensation n'est affichée par ligne que si elle varie (VSS-900-S).
   */
  protected readonly devises = computed(() => {
    const r = this.rapport();
    const unique = (v: (string | null)[]) => [...new Set(v.filter((x): x is string => !!x))];
    const compensation = unique(r?.lignes.map((l) => l.deviseCompensation) ?? []);
    const reglement = unique(r?.lignes.map((l) => l.deviseReglement) ?? []);
    return { compensationVariable: compensation.length > 1, compensation: compensation.length === 1 ? compensation[0] : null,
      reglement: reglement.length === 1 ? reglement[0] : null };
  });

  protected enteteColonne(c: string): string {
    const d = this.devises();
    const estMontant = this.rapport()?.lignes.some((l) => l.valeurs[c]?.nombre !== null && l.valeurs[c] !== undefined);
    if (!estMontant || c.includes('COUNT')) return c;
    const devise = c.includes('CLEARING') ? d.compensation : d.reglement;
    return devise ? `${c} (${devise})` : c;
  }

  /** Titre de section : libellé sans valeur. */
  protected estSection(l: LigneRapportVss): boolean {
    return Object.keys(l.valeurs).length === 0;
  }

  protected estTotal(l: LigneRapportVss): boolean {
    return /^(TOTAL|NET)\b/.test(l.libelle);
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    const r = this.rapport();
    if (!r) return;
    this.erreur.set(null);
    this.telechargement.telecharger(`/api/exploration/vss/rapports/${r.resume.id}/export`, { format })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }
}
