import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { Appro, Arrete, GabService } from './gab.service';

/** Appros et arrêtés en attente de validation (validation à quatre yeux). */
@Component({
  selector: 'app-validation-gab',
  imports: [DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './validation.html',
  styleUrl: './gab.scss',
})
export class ValidationGab {
  private readonly service = inject(GabService);
  protected readonly moi = inject(AuthService).utilisateur;
  protected readonly appros = signal<Appro[]>([]);
  protected readonly arretes = signal<Arrete[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  constructor() {
    this.recharger();
  }

  protected auteur(x: { saisiPar: string; modifiePar: string }): boolean {
    const l = this.moi()?.login;
    return l === x.saisiPar || l === x.modifiePar;
  }

  protected decider(objet: 'appros' | 'arretes', id: number, reference: string, decision: 'validation' | 'annulation'): void {
    let motif: string | undefined;
    if (decision === 'annulation') {
      motif = prompt(`Motif de l'annulation (${reference}) :`) ?? undefined;
      if (!motif) return;
    }
    this.erreur.set(null);
    this.service.decider(objet, id, decision, motif).subscribe({
      next: () => {
        this.message.set(`${objet === 'appros' ? 'Appro' : 'Arrêté'} ${reference} ${decision === 'validation' ? 'validé' : 'annulé'}.`);
        this.recharger();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private recharger(): void {
    this.service.appros({ statut: 'SAISI' }, 0, 200).subscribe((r) => this.appros.set(r.elements));
    this.service.arretes({ statut: 'SAISI' }, 0, 200).subscribe((r) => this.arretes.set(r.elements));
  }
}
