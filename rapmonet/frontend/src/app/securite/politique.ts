import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { messageErreur } from '../core/erreurs';
import { PolitiqueSecurite, SecuriteService } from './securite.service';

/** Politique de mot de passe, de verrouillage et de double authentification. */
@Component({
  selector: 'app-politique',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSlideToggleModule],
  templateUrl: './politique.html',
  styleUrl: './securite.scss',
})
export class Politique {
  private readonly service = inject(SecuriteService);

  protected readonly edition = signal<PolitiqueSecurite | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly enCours = signal(false);

  constructor() {
    this.service.politique().subscribe({
      next: (p) => this.edition.set({ ...p }),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected enregistrer(): void {
    const p = this.edition();
    if (!p) return;
    if (p.deuxFacteursObligatoire && !confirm('La double authentification deviendra obligatoire : chaque utilisateur '
      + 'qui ne l\'a pas encore activée devra le faire à sa prochaine action. Continuer ?')) return;
    this.enCours.set(true);
    this.erreur.set(null);
    this.message.set(null);
    this.service.modifierPolitique(p).subscribe({
      next: (r) => {
        this.edition.set({ ...r });
        this.message.set('Politique enregistrée. Elle s\'applique aux prochains changements de mot de passe et connexions.');
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }
}
