import { Routes } from '@angular/router';
import { MENU } from './core/menu';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'accueil' },
  {
    path: 'accueil',
    title: 'Accueil – RAPMONET',
    loadComponent: () => import('./pages/accueil/accueil').then((m) => m.Accueil),
  },
  // Modules pas encore réalisés : page d'attente, remplacée lot par lot.
  ...MENU.filter((e) => e.lot > 0).map((e) => ({
    path: e.chemin.substring(1),
    title: `${e.libelle} – RAPMONET`,
    data: { entree: e },
    loadComponent: () => import('./pages/a-venir/a-venir').then((m) => m.AVenir),
  })),
  { path: '**', redirectTo: 'accueil' },
];
