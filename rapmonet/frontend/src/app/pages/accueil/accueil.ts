import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AlertesService } from '../../core/alertes.service';
import { InfoSysteme, SystemeService } from '../../core/systeme.service';

type Etat = { statut: 'chargement' } | { statut: 'ok'; info: InfoSysteme } | { statut: 'erreur' };

@Component({
  selector: 'app-accueil',
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  protected readonly alertes = inject(AlertesService);
  protected readonly icones: Record<string, string> = { CRITIQUE: 'error', ATTENTION: 'warning', INFO: 'info' };
  protected readonly libellesNiveau: Record<string, string> = { CRITIQUE: 'Critique', ATTENTION: 'À traiter', INFO: 'Pour information' };

  constructor() {
    this.alertes.rafraichir();
  }

  protected readonly etat = toSignal(
    inject(SystemeService).info().pipe(
      map((info): Etat => ({ statut: 'ok', info })),
      catchError(() => of<Etat>({ statut: 'erreur' })),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
}
