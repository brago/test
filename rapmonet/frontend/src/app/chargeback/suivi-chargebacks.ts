import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, UpperCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { LIBELLES_STATUT } from '../core/ejournal';
import { ChargebackService, CritereChargeback, Dossier, JournalGab, SensChargeback } from './chargeback.service';

/** Suivi des chargebacks GIM (émission ou acquisition selon la route) : cycle de vie, délai, régularisation. */
@Component({
  selector: 'app-suivi-chargebacks',
  imports: [DatePipe, DecimalPipe, UpperCasePipe, FormsModule, RouterLink, MatButtonModule, MatButtonToggleModule, MatCardModule,
    MatCheckboxModule, MatDatepickerModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    MatTableModule, MatTooltipModule],
  templateUrl: './suivi-chargebacks.html',
  styleUrl: './suivi-chargebacks.scss',
})
export class SuiviChargebacks {
  private readonly service = inject(ChargebackService);
  private readonly telechargement = inject(Telechargement);
  protected readonly sens: SensChargeback = inject(ActivatedRoute).snapshot.data['sens'];
  protected readonly emission = this.sens === 'emission';
  protected readonly libellesStatutGab = LIBELLES_STATUT;

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected reference = '';
  protected regularisation = 'NON';
  protected archives = false;

  protected readonly lignes = signal<Dossier[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly taille = signal(25);
  protected readonly binsParametres = signal(true);
  protected readonly selection = signal<Dossier | null>(null);
  protected readonly journal = signal<JournalGab | null>(null);
  protected readonly decision = signal<{ statut: 'OUI' | 'REJET' | 'NON'; commentaire: string } | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  /** Fiche ouverte : l'ARN, l'autorisation et le motif y sont repris, le tableau se resserre. */
  protected readonly colonnes = computed(() => this.selection()
    ? ['regularisation', 'journeeChargeback', 'carte', 'rrn', 'terminal', 'montant', 'situation', 'jours']
    : ['regularisation', 'journeeChargeback', 'carte', 'rrn', 'arn', 'autorisation', 'dateTransaction', 'terminal',
      'montant', 'motif', 'situation', 'jours']);

  constructor() {
    this.rechercher();
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.erreur.set(null);
    this.service.rechercher(this.sens, this.critere(), page, this.taille()).subscribe({
      next: (r) => {
        this.lignes.set(r.page.elements);
        this.total.set(r.page.total);
        this.binsParametres.set(r.binsParametres);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected changerPage(e: PageEvent): void {
    this.taille.set(e.pageSize);
    this.rechercher(e.pageIndex);
  }

  protected ouvrir(d: Dossier): void {
    this.journal.set(null);
    this.decision.set(null);
    this.service.dossier(this.sens, d.id).subscribe((x) => this.selection.set(x));
  }

  protected voirJournal(d: Dossier): void {
    this.service.journalGab(d.id).subscribe({
      next: (j) => this.journal.set(j),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected regulariser(d: Dossier): void {
    this.decision.set({ statut: d.regularisation === 'NON' ? 'OUI' : d.regularisation, commentaire: d.commentaire ?? '' });
  }

  protected enregistrerDecision(d: Dossier): void {
    const x = this.decision();
    if (!x) return;
    this.erreur.set(null);
    this.service.regulariser(this.sens, d.id, x.statut, x.commentaire).subscribe({
      next: (n) => {
        this.selection.set(n);
        this.decision.set(null);
        this.message.set(`Chargeback ${n.arn} : régularisation « ${this.libelleRegul(n.regularisation)} » enregistrée.`);
        this.rechercher(this.page());
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected archiver(): void {
    if (!confirm('Archiver tous les chargebacks régularisés ou rejetés ?')) return;
    this.service.archiver(this.sens).subscribe({
      next: (r) => {
        this.message.set(`${r.archives} chargeback(s) archivé(s).`);
        this.selection.set(null);
        this.rechercher();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.telechargement.telecharger(`/api/chargebacks/${this.sens}/export`, { format, ...this.critere() })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  /** Libellé court de l'état pour le tableau (la situation complète est dans la fiche). */
  protected libelleEtat(d: Dossier): string {
    switch (d.etat) {
      case 'EN_ATTENTE': return this.emission ? 'En attente' : 'À traiter';
      case 'REPRESENTE': return this.emission ? 'Représentation reçue' : 'Représenté';
      case 'SECOND_CHARGEBACK': return 'Second chargeback';
      case 'ANNULE': return 'Annulé';
      default: return this.emission ? 'Acquis' : 'Délai dépassé';
    }
  }

  protected libelleRegul(r: string): string {
    return r === 'OUI' ? 'Régularisé' : r === 'REJET' ? 'Rejeté' : 'Non régularisé';
  }

  protected heure(h: string | null): string {
    return h && h.length === 6 ? `${h.substring(0, 2)}:${h.substring(2, 4)}:${h.substring(4)}` : '';
  }

  private critere(): CritereChargeback {
    return { du: dateIso(this.du), au: dateIso(this.au), reference: this.reference.trim(), regularisation: this.regularisation,
      archives: this.archives ? 'true' : undefined };
  }
}
