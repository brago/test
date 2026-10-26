import { Routes } from '@angular/router';
import { ecrans } from './core/menu';
import { sessionOuverte, sessionRequise } from './core/auth.guard';

/** Écrans réalisés ; les autres entrées du menu affichent la page « À venir ». */
const REALISES = ['/accueil', '/ingestion', '/exploration', '/parametrage',
  '/ingestion/vss', '/exploration/vss', '/parametrage/vss', '/ingestion/ejournal', '/exploration/ejournal',
  '/securite/utilisateurs', '/securite/profils', '/securite/audit', '/securite/politique',
  '/parametrage/gab', '/parametrage/application', '/gab/activite', '/gab/appros', '/gab/arretes', '/gab/validation', '/chargeback/emission', '/chargeback/acquisition',
  '/reporting/kpi', '/reporting/reglementaire', '/compense/traitements', '/compense/commercants', '/compense/validation',
  '/rapprochement/gab-lis', '/rapprochement/regles', '/reclamations/recherche', '/reclamations/registre'];

export const routes: Routes = [
  { path: 'connexion', title: 'Connexion – RAPMONET', loadComponent: () => import('./pages/connexion/connexion').then((m) => m.Connexion) },
  {
    path: 'mot-de-passe',
    title: 'Mot de passe – RAPMONET',
    canActivate: [sessionOuverte],
    loadComponent: () => import('./pages/mot-de-passe/mot-de-passe').then((m) => m.MotDePasse),
  },
  {
    path: 'deux-facteurs',
    title: 'Double authentification – RAPMONET',
    canActivate: [sessionOuverte],
    loadComponent: () => import('./pages/deux-facteurs/deux-facteurs').then((m) => m.DeuxFacteurs),
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
        path: 'securite/politique', title: 'Politique de sécurité – RAPMONET', data: { menu: 'SECURITE_POLITIQUE' },
        loadComponent: () => import('./securite/politique').then((m) => m.Politique),
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
        path: 'ingestion/ejournal', title: 'Chargement des journaux GAB – RAPMONET', data: { menu: 'CHARGEMENT_EJOURNAL' },
        loadComponent: () => import('./ingestion/chargement-ejournal').then((m) => m.ChargementEjournal),
      },
      {
        path: 'exploration/ejournal', title: 'Journaux GAB – RAPMONET', data: { menu: 'EXPLORATION_EJOURNAL' },
        loadComponent: () => import('./exploration/exploration-ejournal').then((m) => m.ExplorationEjournal),
      },
      {
        path: 'ingestion/vss', title: 'Chargement VSS – RAPMONET', data: { menu: 'CHARGEMENT_VSS' },
        loadComponent: () => import('./ingestion/chargement-vss').then((m) => m.ChargementVss),
      },
      {
        path: 'exploration/vss', title: 'Rapports VSS – RAPMONET', data: { menu: 'EXPLORATION_VSS' },
        loadComponent: () => import('./exploration/exploration-vss').then((m) => m.ExplorationVss),
      },
      {
        path: 'parametrage/vss', title: 'Paramétrage VSS – RAPMONET', data: { menu: 'PARAMETRAGE_VSS' },
        loadComponent: () => import('./parametrage/types-rapports-vss').then((m) => m.TypesRapportsVss),
      },
      {
        path: 'compense/traitements', title: 'Compense TPE – RAPMONET', data: { menu: 'COMPENSE_TRAITEMENT' },
        loadComponent: () => import('./compense/traitements').then((m) => m.TraitementsCompense),
      },
      {
        path: 'compense/validation', title: 'Validation compense TPE – RAPMONET', data: { menu: 'COMPENSE_VALIDATION', validation: true },
        loadComponent: () => import('./compense/traitements').then((m) => m.TraitementsCompense),
      },
      {
        path: 'compense/commercants', title: 'Commerçants – RAPMONET', data: { menu: 'COMPENSE_COMMERCANTS' },
        loadComponent: () => import('./compense/commercants').then((m) => m.Commercants),
      },
      {
        path: 'reclamations/recherche', title: 'Recherche porteur – RAPMONET', data: { menu: 'RECLAMATION_RECHERCHE' },
        loadComponent: () => import('./reclamation/recherche-porteur').then((m) => m.RecherchePorteur),
      },
      {
        path: 'reclamations/registre', title: 'Réclamations – RAPMONET', data: { menu: 'RECLAMATION_SUIVI' },
        loadComponent: () => import('./reclamation/registre').then((m) => m.RegistreReclamations),
      },
      {
        path: 'reporting/kpi', title: 'Indicateurs – RAPMONET', data: { menu: 'REPORTING_KPI' },
        loadComponent: () => import('./reporting/kpi').then((m) => m.KpiComponent),
      },
      {
        path: 'reporting/reglementaire', title: 'Réglementaire – RAPMONET', data: { menu: 'REPORTING_REGLEMENTAIRE' },
        loadComponent: () => import('./reporting/reglementaire').then((m) => m.Reglementaire),
      },
      {
        path: 'rapprochement/regles', title: 'Règles de rapprochement – RAPMONET', data: { menu: 'RAPPROCHEMENT_REGLES' },
        loadComponent: () => import('./rapprochement/regles-rapprochement').then((m) => m.ReglesRapprochement),
      },
      {
        path: 'rapprochement/gab-lis', title: 'Rapprochement GAB / LIS – RAPMONET', data: { menu: 'RAPPROCHEMENT_GAB_LIS' },
        loadComponent: () => import('./rapprochement/rapprochement-gab-lis').then((m) => m.RapprochementGabLis),
      },
      {
        path: 'chargeback/emission', title: 'Chargebacks émission – RAPMONET', data: { menu: 'CHARGEBACK_EMISSION', sens: 'emission' },
        loadComponent: () => import('./chargeback/suivi-chargebacks').then((m) => m.SuiviChargebacks),
      },
      {
        path: 'chargeback/acquisition', title: 'Chargebacks acquisition – RAPMONET', data: { menu: 'CHARGEBACK_ACQUISITION', sens: 'acquisition' },
        loadComponent: () => import('./chargeback/suivi-chargebacks').then((m) => m.SuiviChargebacks),
      },
      {
        path: 'parametrage/gab', title: 'Paramétrage GAB – RAPMONET', data: { menu: 'PARAMETRAGE_GAB' },
        loadComponent: () => import('./gab/parametrage-gab').then((m) => m.ParametrageGab),
      },
      {
        path: 'parametrage/application', title: 'Paramètres de l\'application – RAPMONET', data: { menu: 'PARAMETRAGE_APPLICATION' },
        loadComponent: () => import('./parametrage/parametres-application').then((m) => m.ParametresApplication),
      },
      {
        path: 'gab/activite', title: 'Activité des GAB – RAPMONET', data: { menu: 'GAB_ACTIVITE' },
        loadComponent: () => import('./gab/activite').then((m) => m.ActiviteGab),
      },
      {
        path: 'gab/appros', title: 'Approvisionnements GAB – RAPMONET', data: { menu: 'GAB_APPRO' },
        loadComponent: () => import('./gab/appros').then((m) => m.Appros),
      },
      {
        path: 'gab/arretes', title: 'Arrêtés GAB – RAPMONET', data: { menu: 'GAB_ARRETE' },
        loadComponent: () => import('./gab/arretes').then((m) => m.Arretes),
      },
      {
        path: 'gab/validation', title: 'Validation GAB – RAPMONET', data: { menu: 'GAB_VALIDATION' },
        loadComponent: () => import('./gab/validation').then((m) => m.ValidationGab),
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
