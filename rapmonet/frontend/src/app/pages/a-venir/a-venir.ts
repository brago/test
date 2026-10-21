import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { EntreeMenu } from '../../core/menu';

/** Page affichée pour un module dont le lot n'est pas encore réalisé. */
@Component({
  selector: 'app-a-venir',
  imports: [MatIconModule],
  template: `
    <h1>{{ entree().libelle }}</h1>
    <p class="info"><mat-icon>construction</mat-icon> Module prévu au lot {{ entree().lot }}.</p>
  `,
  styles: `.info { display: flex; align-items: center; gap: 8px; }`,
})
export class AVenir {
  readonly entree = input.required<EntreeMenu>();
}
