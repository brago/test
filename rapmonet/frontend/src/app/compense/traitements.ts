import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { Telechargement } from '../core/telechargement';
import { Apercu, CompenseService, LigneCompense, Traitement } from './compense.service';

const LIBELLES: Record<string, string> = { SAISI: 'À valider', VALIDE: 'Validé', ANNULE: 'Annulé' };

/** Traitements de compense TPE : aperçu d'une journée LIS, création, pièces, macro comptable, validation. */
@Component({
  selector: 'app-traitements-compense',
  imports: [DatePipe, DecimalPipe, PercentPipe, FormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatSelectModule, MatTabsModule, MatTooltipModule],
  templateUrl: './traitements.html',
  styleUrl: './compense.scss',
})
export class TraitementsCompense {
  private readonly service = inject(CompenseService);
  private readonly telechargement = inject(Telechargement);
  private readonly auth = inject(AuthService);
  /** Écran « Validation » : seulement les traitements à valider. */
  protected readonly validation = !!inject(ActivatedRoute).snapshot.data['validation'];
  protected readonly peutPreparer = this.auth.peut('COMPENSE_TRAITEMENT') && !this.validation;
  protected readonly peutValider = this.auth.peut('COMPENSE_VALIDATION');
  protected readonly moi = this.auth.utilisateur;
  protected readonly statuts = LIBELLES;
  protected readonly reseaux: { code: LigneCompense['reseau']; libelle: string }[] = [
    { code: 'GIM', libelle: 'GIM UEMOA' }, { code: 'VISA', libelle: 'VISA' }, { code: 'MASTERCARD', libelle: 'MASTERCARD' }];

  protected journee = '';
  protected readonly journees = signal<string[]>([]);
  protected readonly apercu = signal<Apercu | null>(null);
  protected readonly traitements = signal<Traitement[]>([]);
  protected readonly selection = signal<Traitement | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  protected readonly totalApercu = computed(() => {
    const a = this.apercu();
    return a ? { montant: a.lignes.reduce((s, l) => s + l.montant, 0), commission: a.lignes.reduce((s, l) => s + l.commission, 0),
      taf: a.lignes.reduce((s, l) => s + l.taf, 0), frais: a.lignes.reduce((s, l) => s + l.frais, 0) } : null;
  });

  constructor() {
    if (this.peutPreparer) {
      this.service.journees().subscribe((j) => {
        this.journees.set(j);
        if (j.length && !this.journee) this.journee = j[0];
      });
    }
    this.recharger();
  }

  protected voirApercu(): void {
    if (!this.journee) return;
    this.erreur.set(null);
    this.message.set(null);
    this.selection.set(null);
    this.service.apercu(this.journee).subscribe({ next: (a) => this.apercu.set(a), error: (e) => this.erreur.set(messageErreur(e)) });
  }

  protected creer(): void {
    this.erreur.set(null);
    this.service.creer(this.journee).subscribe({
      next: (t) => {
        this.apercu.set(null);
        this.selection.set(t);
        this.message.set(`Traitement ${t.reference} créé : il doit être validé par une autre personne avant la passation des écritures.`);
        this.recharger();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected ouvrir(t: Traitement): void {
    this.apercu.set(null);
    this.service.traitement(t.id).subscribe((x) => this.selection.set(x));
  }

  protected decider(t: Traitement, decision: 'validation' | 'annulation'): void {
    let motif: string | undefined;
    if (decision === 'annulation') {
      motif = prompt(`Motif de l'annulation du traitement ${t.reference} :`) ?? undefined;
      if (!motif) return;
    }
    this.erreur.set(null);
    this.service.decider(t.id, decision, motif).subscribe({
      next: (x) => {
        this.selection.set(x);
        this.message.set(`Traitement ${x.reference} ${decision === 'validation' ? 'validé' : 'annulé'}.`);
        this.recharger();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected excel(t: Traitement): void {
    this.telechargement.telecharger(`/api/compense/traitements/${t.id}/excel`, { brouillon: t.statut === 'SAISI' ? 'true' : undefined })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  protected peutDecider(t: Traitement): boolean {
    return this.peutValider && t.statut === 'SAISI' && this.moi()?.login !== t.creePar;
  }

  protected lignesDe(lignes: LigneCompense[] | null, reseau: string): LigneCompense[] {
    return (lignes ?? []).filter((l) => l.reseau === reseau);
  }

  protected somme(lignes: LigneCompense[], champ: 'montant' | 'cci' | 'commission' | 'taf'): number {
    return lignes.reduce((s, l) => s + Number(l[champ]), 0);
  }

  private recharger(): void {
    this.service.traitements(this.validation ? 'SAISI' : undefined).subscribe((t) => this.traitements.set(t));
  }
}
