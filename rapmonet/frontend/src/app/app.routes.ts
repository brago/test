import { Routes } from '@angular/router';
import { MENU } from './core/menu';

const REALISES = ['/ingestion', '/exploration', '/parametrage'];

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'accueil' },
  {
    path: 'accueil',
    title: 'Accueil – RAPMONET',
    loadComponent: () => import('./pages/accueil/accueil').then((m) => m.Accueil),
  },
  {
    path: 'ingestion',
    title: 'Chargement LIS – RAPMONET',
    loadComponent: () => import('./ingestion/chargement-lis').then((m) => m.ChargementLis),
  },
  {
    path: 'exploration',
    title: 'Exploration LIS – RAPMONET',
    loadComponent: () => import('./exploration/exploration-lis').then((m) => m.ExplorationLis),
  },
  {
    path: 'parametrage',
    title: 'Paramétrage LIS – RAPMONET',
    loadComponent: () => import('./parametrage/format-lis').then((m) => m.FormatLis),
  },
  // Modules pas encore réalisés : page d'attente, remplacée lot par lot.
  ...MENU.filter((e) => e.lot > 0 && !REALISES.includes(e.chemin)).map((e) => ({
    path: e.chemin.substring(1),
    title: `${e.libelle} – RAPMONET`,
    data: { entree: e },
    loadComponent: () => import('./pages/a-venir/a-venir').then((m) => m.AVenir),
  })),
  { path: '**', redirectTo: 'accueil' },
];
