import { Routes } from '@angular/router';
import { ecrans } from './core/menu';
import { sessionOuverte, sessionRequise } from './core/auth.guard';

/** Écrans réalisés ; les autres entrées du menu affichent la page « À venir ». */
const REALISES = ['/accueil', '/ingestion', '/exploration', '/parametrage',
  '/securite/utilisateurs', '/securite/profils', '/securite/audit'];

export const routes: Routes = [
  { path: 'connexion', title: 'Connexion – RAPMONET', loadComponent: () => import('./pages/connexion/connexion').then((m) => m.Connexion) },
  {
    path: 'mot-de-passe',
    title: 'Mot de passe – RAPMONET',
    canActivate: [sessionOuverte],
    loadComponent: () => import('./pages/mot-de-passe/mot-de-passe').then((m) => m.MotDePasse),
  },
  {
    path: '',
    canActivateChild: [sessionRequise],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'accueil' },
      { path: 'accueil', title: 'Accueil – RAPMONET', loadComponent: () => import('./pages/accueil/accueil').then((m) => m.Accueil) },
      {
        path: 'securite/utilisateurs', title: 'Utilisateurs – RAPMONET', data: { menu: 'SECURITE_UTILISATEURS' },
        loadComponent: () => import('./securite/utilisateurs').then((m) => m.Utilisateurs),
      },
      {
        path: 'securite/profils', title: 'Profils – RAPMONET', data: { menu: 'SECURITE_PROFILS' },
        loadComponent: () => import('./securite/profils').then((m) => m.Profils),
      },
      {
        path: 'securite/audit', title: 'Piste d\'audit – RAPMONET', data: { menu: 'SECURITE_AUDIT' },
        loadComponent: () => import('./securite/audit').then((m) => m.Audit),
      },
      {
        path: 'ingestion', title: 'Chargement LIS – RAPMONET', data: { menu: 'CHARGEMENT_LIS' },
        loadComponent: () => import('./ingestion/chargement-lis').then((m) => m.ChargementLis),
      },
      {
        path: 'exploration', title: 'Exploration LIS – RAPMONET', data: { menu: 'EXPLORATION_LIS' },
        loadComponent: () => import('./exploration/exploration-lis').then((m) => m.ExplorationLis),
      },
      {
        path: 'parametrage', title: 'Paramétrage LIS – RAPMONET', data: { menu: 'PARAMETRAGE_LIS' },
        loadComponent: () => import('./parametrage/format-lis').then((m) => m.FormatLis),
      },
      ...ecrans()
        .filter((e) => e.chemin && !REALISES.includes(e.chemin))
        .map((e) => ({
          path: e.chemin!.substring(1),
          title: `${e.libelle} – RAPMONET`,
          data: { entree: e, menu: e.code },
          loadComponent: () => import('./pages/a-venir/a-venir').then((m) => m.AVenir),
        })),
    ],
  },
  { path: '**', redirectTo: 'accueil' },
];
