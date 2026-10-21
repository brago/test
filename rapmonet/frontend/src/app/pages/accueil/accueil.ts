import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { InfoSysteme, SystemeService } from '../../core/systeme.service';

type Etat = { statut: 'chargement' } | { statut: 'ok'; info: InfoSysteme } | { statut: 'erreur' };

@Component({
  selector: 'app-accueil',
  imports: [MatCardModule, MatIconModule],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  protected readonly etat = toSignal(
    inject(SystemeService).info().pipe(
      map((info): Etat => ({ statut: 'ok', info })),
      catchError(() => of<Etat>({ statut: 'erreur' })),
    ),
    { initialValue: { statut: 'chargement' } as Etat },
  );
}
