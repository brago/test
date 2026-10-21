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
import { dateIso, premier } from '../core/format';
import { ParametrageService } from '../parametrage/parametrage.service';
import { TypeLis } from '../ingestion/lis.service';
import { DetailTransaction, ExplorationLisService, TransactionLis } from './exploration-lis.service';

/** Colonnes de synthèse : les codes d'attributs diffèrent selon le TC, on prend le premier présent. */
const COLONNES_ATTRIBUTS: Record<string, string[]> = {
  date: ['LIS_TRANSACTION_DATE', 'LIS_SETTLEMENT_DATE', 'LIS_REMITTANCE_DEPOSIT_DATE_6'],
  montant: ['LIS_SOURCE_AMOUNT', 'LIS_TRANSACTION_AMOUNT', 'LIS_NET_AMOUNT_IN_CFA', 'LIS_AMOUNT_IN_REMITTANCE_CURRENCY_12'],
  carte: ['LIS_CARDHOLDER_CARD_NUMBER_INVOLVED_IN_TRANSACTION'],
  libelle: ['LIS_MERCHANT_NAME', 'LIS_MERCHANT_ACRONYM', 'LIS_TYPE_OF_REPORTING'],
  terminal: ['LIS_CARD_ACCEPTOR_TERMINAL_IDENTIFICATION', 'LIS_TERMINAL_NUMBER'],
  autorisation: ['LIS_AUTHORISATION_CODE'],
  reference: ['LIS_ACQUIRERS_REFERENCE_NUMBER', 'LIS_ACQUIRERS_MICROFILM_REFERENCE_NUMBER', 'LIS_RETRIEVAL_REFERENCE_NUMBER'],
};

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

  protected valeur(t: TransactionLis, colonne: string): unknown {
    return premier(t.attributs, COLONNES_ATTRIBUTS[colonne]);
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.chargement.set(true);
    this.service
      .rechercher({ du: dateIso(this.du), au: dateIso(this.au), typeLis: this.typeLis, tc: this.tc }, page, this.taille())
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
