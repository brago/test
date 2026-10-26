import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { LIBELLES_STATUT } from '../core/ejournal';
import { messageErreur } from '../core/erreurs';
import { dateIso } from '../core/format';
import { OperationLiee, ReclamationService, Referentiels, ResultatPorteur } from './reclamation.service';

/**
 * Recherche porteur : opérations d'une carte, d'un compte ou d'une référence dans les journaux GAB, les LIS et les
 * chargebacks, puis ouverture d'une réclamation avec les opérations choisies. Le numéro de carte complet reste dans
 * le navigateur et n'est envoyé qu'en corps de requête.
 */
@Component({
  selector: 'app-recherche-porteur',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, MatButtonModule, MatCardModule, MatCheckboxModule,
    MatDatepickerModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatTooltipModule],
  templateUrl: './recherche-porteur.html',
  styleUrl: './reclamation.scss',
})
export class RecherchePorteur {
  private readonly service = inject(ReclamationService);
  private readonly router = inject(Router);
  protected readonly peutSuivre = inject(AuthService).peut('RECLAMATION_SUIVI');
  protected readonly statutsOperation = LIBELLES_STATUT;

  protected carte = '';
  protected compte = '';
  protected reference = '';
  protected du: Date | null = null;
  protected au: Date | null = null;

  protected readonly resultat = signal<ResultatPorteur | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  /** Opérations choisies, par « TYPE:id ». */
  protected readonly choix = signal<Map<string, OperationLiee>>(new Map());
  protected readonly referentiels = signal<Referentiels | null>(null);
  protected readonly saisie = signal<{ client: string; contact: string; canal: string; objet: string; description: string;
    montant: number | null } | null>(null);

  protected readonly montantChoisi = computed(() => [...this.choix().values()].reduce((s, o) => s + (o.montant ?? 0), 0));

  constructor() {
    this.service.referentiels().subscribe((r) => this.referentiels.set(r));
  }

  protected rechercher(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.saisie.set(null);
    this.choix.set(new Map());
    this.service.rechercher({ carte: this.carte, compte: this.compte, reference: this.reference,
      du: dateIso(this.du), au: dateIso(this.au) }).subscribe({
      next: (r) => { this.resultat.set(r); this.enCours.set(false); },
      error: (e) => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  protected choisi(type: string, id: number): boolean {
    return this.choix().has(type + ':' + id);
  }

  protected basculer(o: OperationLiee): void {
    const m = new Map(this.choix());
    const cle = o.type + ':' + o.id;
    if (m.has(cle)) m.delete(cle); else m.set(cle, o);
    this.choix.set(m);
  }

  protected ouvrirSaisie(): void {
    const r = this.referentiels();
    this.saisie.set({ client: '', contact: '', canal: 'GUICHET', objet: Object.keys(r?.objets ?? {})[0] ?? 'AUTRE',
      description: '', montant: this.montantChoisi() || null });
    setTimeout(() => document.getElementById('saisie-reclamation')?.scrollIntoView({ behavior: 'smooth' }));
  }

  protected enregistrer(): void {
    const s = this.saisie();
    const r = this.resultat();
    if (!s || !r) return;
    const operations = [...this.choix().values()];
    const dates = operations.map((o) => o.date).filter((d): d is string => !!d).sort();
    // Compte imprimé sur les tickets choisis, si aucun compte n'a été saisi.
    const compteTicket = r.operationsGab.find((x) => this.choisi('GAB', x.operation.id) && x.operation.compte)?.operation.compte ?? null;
    this.service.ouvrir({
      carte: this.carte.trim() || r.carteMasquee, compte: this.compte.trim() || compteTicket, client: s.client, contact: s.contact || null,
      canal: s.canal, objet: s.objet, description: s.description, montantConteste: s.montant,
      dateOperation: dates.length ? dates[0].slice(0, 10) : null, operations,
    }).subscribe({
      next: (rec) => this.router.navigate(['/reclamations/registre'], { queryParams: { id: rec.id } }),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected operationGab(x: ResultatPorteur['operationsGab'][number]): OperationLiee {
    const o = x.operation;
    return { type: 'GAB', id: o.id, date: o.dateHeure, montant: o.montant,
      libelle: `${o.type === 'RETRAIT' ? 'Retrait' : 'Solde'} GAB ${o.terminal} – ${this.statutsOperation[o.statut] ?? o.statut}`
        + (o.reference ? ` – réf. ${o.reference}` : '') + (x.rapprochement ? ` – ${x.rapprochement.resultat}` : '') };
  }

  protected operationLis(t: ResultatPorteur['transactionsLis'][number]): OperationLiee {
    return { type: 'LIS', id: t.id, date: t.date, montant: t.montant === null ? null : Number(t.montant),
      libelle: `LIS ${t.typeLis} ${t.journee} – TC ${t.tc} ${t.libelleTc ?? ''} – ${t.libelle ?? ''}`
        + (t.autorisation ? ` – autor. ${t.autorisation}` : '') };
  }

  protected operationChargeback(c: ResultatPorteur['chargebacks'][number]): OperationLiee {
    return { type: 'CHARGEBACK', id: c.id, date: c.dateTransaction, montant: c.montant,
      libelle: `Chargeback ${c.sens === 'EMISSION' ? 'émission' : 'acquisition'} ARN ${c.arn} – ${c.situation ?? c.etat ?? ''}` };
  }

  protected classeRapprochement(r: string | undefined): string {
    if (!r) return 'gris';
    return r === 'Rapproché' ? 'vert' : r.startsWith('Carte de la banque') ? 'bleu' : r.startsWith('En attente') ? 'orange' : 'rouge';
  }

  protected nbChoisis(): number {
    return this.choix().size;
  }

  protected objets(): [string, string][] {
    return Object.entries(this.referentiels()?.objets ?? {});
  }

  protected canaux(): [string, string][] {
    return Object.entries(this.referentiels()?.canaux ?? {});
  }
}
