import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { LigneRapprochement, Rapprochement, RapprochementService, TraitementEcart, VueRapprochement } from './rapprochement.service';

const LIBELLES_TRAITEMENT: Record<string, string> = { A_TRAITER: 'À traiter', JUSTIFIE: 'Justifié', REGULARISE: 'Régularisé' };

/** Rapprochement des retraits de cartes confrères sur nos GAB : journaux NCR / avis d'acquisition LIS. */
@Component({
  selector: 'app-rapprochement-gab-lis',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatButtonToggleModule, MatCardModule, MatDatepickerModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatTableModule, MatTooltipModule],
  templateUrl: './rapprochement-gab-lis.html',
  styleUrl: './rapprochement.scss',
})
export class RapprochementGabLis {
  private readonly service = inject(RapprochementService);
  private readonly telechargement = inject(Telechargement);
  protected readonly libellesTraitement = LIBELLES_TRAITEMENT;

  protected du: Date = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  protected au: Date = new Date();
  protected terminal = '';
  protected vue: VueRapprochement = 'ECARTS';

  protected readonly resultat = signal<Rapprochement | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly edition = signal<{ ligne: LigneRapprochement; statut: TraitementEcart; commentaire: string } | null>(null);
  protected readonly colonnes = ['resultat', 'terminal', 'journal', 'lis', 'carte', 'montant', 'ecart', 'traitement'];

  protected rapprocher(): void {
    const du = dateIso(this.du), au = dateIso(this.au);
    if (!du || !au) {
      this.erreur.set('Indiquez la période.');
      return;
    }
    this.erreur.set(null);
    this.enCours.set(true);
    this.service.rapprocher(du, au, this.terminal.trim(), this.vue).subscribe({
      next: (r) => { this.resultat.set(r); this.enCours.set(false); },
      error: (e) => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  protected changerVue(v: VueRapprochement): void {
    this.vue = v;
    if (this.resultat()) this.rapprocher();
  }

  protected traiter(l: LigneRapprochement): void {
    this.message.set(null);
    this.edition.set({ ligne: l, statut: l.traitement === 'A_TRAITER' ? 'JUSTIFIE' : (l.traitement ?? 'JUSTIFIE'),
      commentaire: l.commentaire ?? '' });
  }

  protected enregistrer(): void {
    const e = this.edition();
    if (!e) return;
    this.service.traiter(e.ligne, e.statut, e.commentaire).subscribe({
      next: () => {
        this.edition.set(null);
        this.message.set(`Écart ${LIBELLES_TRAITEMENT[e.statut].toLowerCase()}.`);
        this.rapprocher();
      },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.telechargement.telecharger('/api/rapprochement/gab-lis/export', { format, du: dateIso(this.du), au: dateIso(this.au),
      terminal: this.terminal.trim(), vue: this.vue }).subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }
}
