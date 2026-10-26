import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService, Enrolement } from '../../core/auth.service';
import { messageErreur } from '../../core/erreurs';

/** Activation (QR code + premier code) ou désactivation de la double authentification du compte. */
@Component({
  selector: 'app-deux-facteurs',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './deux-facteurs.html',
  styleUrl: '../mot-de-passe/mot-de-passe.scss',
})
export class DeuxFacteurs {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly assainisseur = inject(DomSanitizer);

  protected readonly obligatoire = computed(() => !!this.auth.utilisateur()?.doitActiverDeuxFacteurs);
  protected readonly active = computed(() => !!this.auth.utilisateur()?.deuxFacteurs);
  protected readonly enrolement = signal<Enrolement | null>(null);
  /** QR code produit par le serveur (SVG sans script), affiché tel quel. */
  protected readonly qr = computed<SafeHtml | null>(() => {
    const e = this.enrolement();
    return e ? this.assainisseur.bypassSecurityTrustHtml(e.qrSvg) : null;
  });
  protected readonly enCours = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected code = '';
  protected motDePasse = '';

  constructor() {
    if (!this.active()) this.commencer();
  }

  protected commencer(): void {
    this.erreur.set(null);
    this.auth.enrolement().subscribe({
      next: (e) => this.enrolement.set(e),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected codeComplet(): boolean {
    return /^\d{6}$/.test(this.code.replace(/\s/g, ''));
  }

  protected activer(): void {
    if (!this.codeComplet()) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.activerDeuxFacteurs(this.code.replace(/\s/g, '')).subscribe({
      next: () => {
        this.enCours.set(false);
        this.enrolement.set(null);
        this.code = '';
        this.message.set('Double authentification activée : le code vous sera demandé à chaque connexion.');
        if (this.router.url.startsWith('/deux-facteurs') && this.obligatoireAvant) this.router.navigate(['/accueil']);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.code = '';
        this.enCours.set(false);
      },
    });
  }

  /** Mémorisé à l'ouverture : après une activation obligatoire, l'utilisateur rejoint l'accueil. */
  private readonly obligatoireAvant = this.obligatoire();

  protected desactiver(): void {
    if (!this.motDePasse) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.auth.desactiverDeuxFacteurs(this.motDePasse).subscribe({
      next: () => {
        this.enCours.set(false);
        this.motDePasse = '';
        this.message.set('Double authentification désactivée.');
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected quitter(): void {
    if (this.obligatoire()) {
      this.auth.deconnecter().subscribe();
    } else {
      this.router.navigate(['/accueil']);
    }
  }
}
