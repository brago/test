import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService, RegleMotDePasse, etapeRequise } from '../../core/auth.service';
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

  /** Règles de la politique en vigueur, lues sur le serveur. */
  private readonly politique = signal<RegleMotDePasse[]>([]);
  protected readonly expire = computed(() => {
    const e = this.auth.utilisateur()?.expirationMotDePasse;
    return !!e && new Date(e + 'T00:00:00').getTime() <= Date.now();
  });

  protected readonly regles = computed(() => {
    const n = this.nouveau();
    const login = this.auth.utilisateur()?.login.toLowerCase() ?? '';
    return [
      ...this.politique().map((r) => ({ libelle: r.libelle, ok: new RegExp(r.motif, 'u').test(n) })),
      { libelle: 'Sans votre identifiant', ok: n.length > 0 && (!login || !n.toLowerCase().includes(login)) },
      { libelle: 'Confirmation identique', ok: n.length > 0 && n === this.confirmation() },
    ];
  });

  constructor() {
    this.auth.reglesMotDePasse().subscribe((r) => this.politique.set(r));
  }
  protected readonly valide = computed(() => this.regles().every((r) => r.ok));

  protected enregistrer(): void {
    if (!this.valide()) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.changerMotDePasse(this.actuel, this.nouveau()).subscribe({
      next: (u) => this.router.navigateByUrl(etapeRequise(u) ?? '/accueil'),
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
