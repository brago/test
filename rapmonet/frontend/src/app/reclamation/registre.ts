import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { Reclamation, ReclamationService, ResumeReclamation } from './reclamation.service';

const ACTIONS: Record<string, string> = {
  OUVERTURE: 'Ouverture', PRISE_EN_CHARGE: 'Prise en charge', COMMENTAIRE: 'Commentaire', CLOTURE: 'Clôture', REOUVERTURE: 'Réouverture',
};

/** Registre des réclamations porteurs : liste, fiche (imprimable), prise en charge, suivi et clôture. */
@Component({
  selector: 'app-registre-reclamations',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, MatButtonModule, MatButtonToggleModule, MatFormFieldModule,
    MatIconModule, MatInputModule],
  templateUrl: './registre.html',
  styleUrl: './reclamation.scss',
})
export class RegistreReclamations {
  private readonly service = inject(ReclamationService);
  private readonly router = inject(Router);
  protected readonly peutRechercher = inject(AuthService).peut('RECLAMATION_RECHERCHE');
  protected readonly actions = ACTIONS;

  protected statut = 'A_TRAITER';
  protected recherche = '';
  protected commentaire = '';
  protected readonly liste = signal<ResumeReclamation[]>([]);
  protected readonly fiche = signal<Reclamation | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly maintenant = new Date();

  constructor() {
    const id = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('id'));
    this.charger();
    if (id) this.ouvrir(id);
  }

  protected charger(): void {
    this.service.lister(this.statut, this.recherche.trim()).subscribe({
      next: (l) => this.liste.set(l), error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected ouvrir(id: number): void {
    this.erreur.set(null);
    this.commentaire = '';
    this.service.detail(id).subscribe({
      next: (r) => {
        this.fiche.set(r);
        this.router.navigate([], { queryParams: { id }, replaceUrl: true });
        setTimeout(() => document.getElementById('fiche')?.scrollIntoView({ behavior: 'smooth' }));
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected agir(action: string): void {
    const r = this.fiche();
    if (!r) return;
    this.service.agir(r.id, action, this.commentaire.trim() || null).subscribe({
      next: (x) => { this.fiche.set(x); this.commentaire = ''; this.erreur.set(null); this.charger(); },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected close(r: Reclamation): boolean {
    return r.statut === 'FONDEE' || r.statut === 'NON_FONDEE';
  }

  protected imprimer(): void {
    window.print();
  }
}
