import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../core/auth.service';
import { messageErreur } from '../../core/erreurs';

/** Changement du mot de passe : obligatoire à la première connexion ou après réinitialisation. */
@Component({
  selector: 'app-mot-de-passe',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './mot-de-passe.html',
  styleUrl: './mot-de-passe.scss',
})
export class MotDePasse {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected actuel = '';
  protected readonly nouveau = signal('');
  protected readonly confirmation = signal('');
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly obligatoire = computed(() => !!this.auth.utilisateur()?.doitChangerMotDePasse);

  protected readonly regles = computed(() => {
    const n = this.nouveau();
    return [
      { libelle: 'Au moins 8 caractères', ok: n.length >= 8 },
      { libelle: 'Au moins une lettre', ok: /\p{L}/u.test(n) },
      { libelle: 'Au moins un chiffre', ok: /\d/.test(n) },
      { libelle: 'Confirmation identique', ok: n.length > 0 && n === this.confirmation() },
    ];
  });
  protected readonly valide = computed(() => this.regles().every((r) => r.ok));

  protected enregistrer(): void {
    if (!this.valide()) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.changerMotDePasse(this.actuel, this.nouveau()).subscribe({
      next: () => this.router.navigate(['/accueil']),
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected annuler(): void {
    if (this.obligatoire()) {
      this.auth.deconnecter().subscribe();
    } else {
      this.router.navigate(['/accueil']);
    }
  }
}
