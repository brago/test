import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { ParametrageService } from '../parametrage/parametrage.service';
import { TypeLis } from '../ingestion/lis.service';
import { DetailTransaction, ExplorationLisService, TransactionLis } from './exploration-lis.service';

@Component({
  selector: 'app-exploration-lis',
  imports: [
    DatePipe, DecimalPipe, FormsModule,
    MatButtonModule, MatCardModule, MatDatepickerModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule,
  ],
  templateUrl: './exploration-lis.html',
  styleUrl: './exploration-lis.scss',
})
export class ExplorationLis {
  private readonly service = inject(ExplorationLisService);
  private readonly telechargement = inject(Telechargement);

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected typeLis: TypeLis | '' = '';
  protected tc = '';

  protected readonly tcs = toSignal(
    inject(ParametrageService).format('LIS_GIM').pipe(
      map((f) => Object.values(f.tcs).filter((t) => !f.sousFichiers.some((s) => s.tcEntete === t.code || s.tcFin === t.code)
        && t.code !== f.tcEntete && t.code !== f.tcFin)),
    ),
    { initialValue: [] },
  );

  protected readonly page = signal(0);
  protected readonly taille = signal(50);
  protected readonly total = signal(0);
  protected readonly lignes = signal<TransactionLis[]>([]);
  protected readonly chargement = signal(false);
  protected readonly detail = signal<DetailTransaction | null>(null);

  protected readonly colonnes = ['journee', 'typeLis', 'sousFichier', 'tc', 'date', 'montant', 'carte', 'libelle', 'terminal', 'autorisation', 'reference'];
  protected readonly colonnesAttributs = ['position', 'code', 'libelle', 'brut', 'valeur'];

  constructor() {
    this.rechercher();
  }

  protected readonly exportEnCours = signal(false);
  protected readonly erreurExport = signal<string | null>(null);

  private critere() {
    return { du: dateIso(this.du), au: dateIso(this.au), typeLis: this.typeLis, tc: this.tc };
  }

  /** Exporte toutes les transactions correspondant aux filtres (pas seulement la page affichée). */
  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.exportEnCours.set(true);
    this.erreurExport.set(null);
    this.telechargement.telecharger('/api/exploration/lis/transactions/export', { format, ...this.critere() }).subscribe({
      next: () => this.exportEnCours.set(false),
      error: (e: Error) => {
        this.erreurExport.set(e.message);
        this.exportEnCours.set(false);
      },
    });
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.chargement.set(true);
    this.service
      .rechercher(this.critere(), page, this.taille())
      .subscribe({
        next: (r) => {
          this.lignes.set(r.elements);
          this.total.set(r.total);
          this.chargement.set(false);
        },
        error: () => this.chargement.set(false),
      });
  }

  protected changerPage(e: PageEvent): void {
    this.taille.set(e.pageSize);
    this.rechercher(e.pageIndex);
  }

  protected reinitialiser(): void {
    this.du = this.au = null;
    this.typeLis = '';
    this.tc = '';
    this.rechercher();
  }

  protected ouvrir(t: TransactionLis): void {
    this.service.detail(t.id).subscribe((d) => this.detail.set(d));
  }

  protected estNombre(v: unknown): boolean {
    return typeof v === 'number';
  }
}
