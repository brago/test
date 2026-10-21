import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../core/auth.service';
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
  protected readonly expiree = !!this.route.snapshot.queryParamMap.get('expiree');

  constructor() {
    // Initialise le cookie anti-CSRF et redirige si une session existe déjà.
    this.auth.charger(true).subscribe((u) => {
      if (u) this.suivant(u.doitChangerMotDePasse);
    });
  }

  protected connecter(): void {
    if (!this.login || !this.motDePasse) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.connecter(this.login, this.motDePasse).subscribe({
      next: (u) => this.suivant(u.doitChangerMotDePasse),
      error: (e) => {
        this.erreur.set(messageErreur(e, 'Connexion impossible.'));
        this.motDePasse = '';
        this.enCours.set(false);
      },
    });
  }

  private suivant(doitChanger: boolean): void {
    const retour = this.route.snapshot.queryParamMap.get('retour') || '/accueil';
    this.router.navigateByUrl(doitChanger ? '/mot-de-passe' : retour);
  }
}
