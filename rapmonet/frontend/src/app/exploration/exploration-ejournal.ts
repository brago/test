import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import {
  CritereJournal, EjournalService, EvenementGab, LIBELLES_ACTION, LIBELLES_STATUT, OperationGab, SessionGab,
} from '../core/ejournal';

/** Consultation des opérations des journaux GAB, avec l'extrait du journal et les passages superviseur. */
@Component({
  selector: 'app-exploration-ejournal',
  imports: [
    DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCheckboxModule, MatCardModule, MatDatepickerModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule,
    MatTabsModule, MatTooltipModule,
  ],
  templateUrl: './exploration-ejournal.html',
  styleUrl: './exploration-ejournal.scss',
})
export class ExplorationEjournal {
  private readonly service = inject(EjournalService);
  private readonly telechargement = inject(Telechargement);

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected terminal = '';
  protected type = '';
  protected statut = '';
  protected codeReponse = '';
  protected carte = '';
  protected compte = '';
  protected ecartBillets = false;

  protected readonly terminaux = signal<string[]>([]);
  protected readonly lignes = signal<OperationGab[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly taille = signal(50);
  protected readonly chargement = signal(false);
  protected readonly session = signal<SessionGab | null>(null);
  protected readonly selection = signal<number | null>(null);
  protected readonly evenements = signal<EvenementGab[]>([]);
  protected readonly evenementOuvert = signal<number | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly libellesStatut = LIBELLES_STATUT;
  protected readonly libellesAction = LIBELLES_ACTION;
  protected readonly types = ['TYPE1', 'TYPE2', 'TYPE3', 'TYPE4'];
  protected readonly colonnes = ['dateHeure', 'terminal', 'type', 'carte', 'montant', 'reponse', 'statut', 'compte', 'billets', 'incidents'];

  constructor() {
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    this.terminal = q.get('terminal') ?? '';
    if (q.get('du')) this.du = new Date(q.get('du') + 'T00:00:00');
    if (q.get('au')) this.au = new Date(q.get('au') + 'T00:00:00');
    this.service.terminaux().subscribe((t) => this.terminaux.set(t));
    this.rechercher();
  }

  private critere(): CritereJournal {
    return {
      du: dateIso(this.du), au: dateIso(this.au), terminal: this.terminal, type: this.type, statut: this.statut,
      codeReponse: this.codeReponse.trim(), carte: this.carte.trim(), compte: this.compte.trim(),
      ecartBillets: this.ecartBillets ? 'true' : undefined,
    };
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.chargement.set(true);
    this.service.operations(this.critere(), page, this.taille()).subscribe({
      next: (r) => {
        this.lignes.set(r.elements);
        this.total.set(r.total);
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
    this.service.evenements({ du: dateIso(this.du), au: dateIso(this.au), terminal: this.terminal })
      .subscribe((e) => this.evenements.set(e));
  }

  protected changerPage(e: PageEvent): void {
    this.taille.set(e.pageSize);
    this.rechercher(e.pageIndex);
  }

  protected ouvrir(o: OperationGab): void {
    this.selection.set(o.id);
    this.service.session(o.sessionId).subscribe((s) => this.session.set(s));
  }

  /** Valeur de billets, par type de cassette (nombre × coupure paramétrée). */
  protected valeurs(nombres: number[] | null, s: SessionGab): number[] {
    return (nombres ?? []).map((n, i) => n * (s.coupures?.[i] ?? 0));
  }

  protected somme(v: number[]): number {
    return v.reduce((a, b) => a + b, 0);
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.erreur.set(null);
    this.telechargement.telecharger('/api/exploration/ejournal/operations/export', { format, ...this.critere() })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }
}
