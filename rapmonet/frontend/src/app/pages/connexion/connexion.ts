import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService, UtilisateurConnecte, etapeRequise } from '../../core/auth.service';
import { messageErreur } from '../../core/erreurs';

@Component({
  selector: 'app-connexion',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './connexion.html',
  styleUrl: './connexion.scss',
})
export class Connexion {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected login = '';
  protected motDePasse = '';
  protected readonly masque = signal(true);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  /** Mot de passe accepté : saisie du code de l'application d'authentification. */
  protected readonly etapeCode = signal(false);
  protected code = '';
  protected readonly expiree = !!this.route.snapshot.queryParamMap.get('expiree');

  constructor() {
    // Initialise le cookie anti-CSRF et redirige si une session existe déjà.
    this.auth.charger(true).subscribe((u) => {
      if (u) this.suivant(u);
    });
  }

  protected connecter(): void {
    if (!this.login || !this.motDePasse) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.connecter(this.login, this.motDePasse).subscribe({
      next: (r) => {
        if ('codeRequis' in r) {
          this.motDePasse = '';
          this.code = '';
          this.etapeCode.set(true);
          this.enCours.set(false);
        } else {
          this.suivant(r);
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e, 'Connexion impossible.'));
        this.motDePasse = '';
        this.enCours.set(false);
      },
    });
  }

  protected valider(): void {
    const code = this.code.replace(/\s/g, '');
    if (!/^\d{6}$/.test(code)) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.verifierCode(code).subscribe({
      next: (u) => this.suivant(u),
      error: (e) => {
        const message = messageErreur(e, 'Code refusé.');
        this.erreur.set(message);
        this.code = '';
        this.enCours.set(false);
        // Compte verrouillé ou délai dépassé : retour à la saisie du mot de passe.
        if (/verrouillé|mot de passe/i.test(message)) this.etapeCode.set(false);
      },
    });
  }

  protected codeComplet(): boolean {
    return /^\d{6}$/.test(this.code.replace(/\s/g, ''));
  }

  protected retour(): void {
    this.etapeCode.set(false);
    this.erreur.set(null);
  }

  private suivant(u: UtilisateurConnecte): void {
    const retour = this.route.snapshot.queryParamMap.get('retour') || '/accueil';
    this.router.navigateByUrl(etapeRequise(u) ?? retour);
  }
}
