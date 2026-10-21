import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { TypeRapportVss, VssService } from '../core/vss';

/** Choix des rapports VSS à enregistrer (équivalent des fichiers EP* cochés de MONETICA). */
@Component({
  selector: 'app-types-rapports-vss',
  imports: [MatCardModule, MatIconModule, MatSlideToggleModule],
  template: `
    <h1>Paramétrage – Rapports VSS</h1>
    <p class="aide">
      Rapports VisaNet Settlement Service lus dans les fichiers VX (EP-747). Seuls les rapports activés sont enregistrés
      à l'intégration. Un rapport absent de cette liste est enregistré avec un avertissement.
    </p>
    @if (erreur(); as e) { <p class="ko"><mat-icon>error</mat-icon> {{ e }}</p> }
    <mat-card appearance="outlined" class="liste">
      @for (t of types(); track t.code) {
        <div class="type">
          <mat-slide-toggle [checked]="t.integre" (change)="basculer(t, $event.checked)" [disabled]="!modifiable">
          </mat-slide-toggle>
          <div>
            <div><strong>{{ t.code }}</strong> – {{ t.libelle }}</div>
            <div class="aide">{{ t.description }}</div>
          </div>
        </div>
      }
    </mat-card>
  `,
  styles: `
    .aide { color: var(--mat-sys-on-surface-variant); font-size: 13px; max-width: 900px; }
    .liste { max-width: 900px; padding: 8px 16px; }
    .type { display: flex; gap: 16px; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .type:last-child { border-bottom: none; }
    .ko { color: var(--mat-sys-error); display: flex; gap: 8px; align-items: center; }
  `,
})
export class TypesRapportsVss {
  private readonly service = inject(VssService);
  protected readonly modifiable = inject(AuthService).peut('PARAMETRAGE_VSS');
  protected readonly types = signal<TypeRapportVss[]>([]);
  protected readonly erreur = signal<string | null>(null);

  constructor() {
    this.service.types().subscribe((t) => this.types.set(t));
  }

  protected basculer(t: TypeRapportVss, integre: boolean): void {
    this.service.modifierType(t.code, integre).subscribe({
      next: (maj) => this.types.update((l) => l.map((x) => (x.code === maj.code ? maj : x))),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
