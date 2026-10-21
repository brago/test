/**
 * Menu principal. Chaque entrée porte le code du menu côté serveur (table securite_menu) :
 * elle n'est affichée que si le profil de l'utilisateur l'autorise.
 */
export interface EntreeMenu {
  code: string;
  libelle: string;
  icone: string;
  /** Lien de l'écran ; absent pour un groupe. */
  chemin?: string;
  /** Lot de livraison ; les écrans pas encore réalisés affichent « À venir ». */
  lot: number;
  enfants?: EntreeMenu[];
}

export const MENU: EntreeMenu[] = [
  { code: 'ACCUEIL', libelle: 'Accueil', icone: 'home', chemin: '/accueil', lot: 0 },
  {
    code: 'SECURITE', libelle: 'Sécurité & habilitations', icone: 'admin_panel_settings', lot: 1,
    enfants: [
      { code: 'SECURITE_UTILISATEURS', libelle: 'Utilisateurs', icone: 'group', chemin: '/securite/utilisateurs', lot: 1 },
      { code: 'SECURITE_PROFILS', libelle: 'Profils', icone: 'badge', chemin: '/securite/profils', lot: 1 },
      { code: 'SECURITE_AUDIT', libelle: 'Piste d\'audit', icone: 'policy', chemin: '/securite/audit', lot: 1 },
    ],
  },
  {
    code: 'PARAMETRAGE', libelle: 'Paramétrage', icone: 'tune', lot: 2,
    enfants: [
      { code: 'PARAMETRAGE_LIS', libelle: 'Format LIS', icone: 'description', chemin: '/parametrage', lot: 2 },
      { code: 'PARAMETRAGE_VSS', libelle: 'Rapports VSS', icone: 'fact_check', chemin: '/parametrage/vss', lot: 3 },
      { code: 'PARAMETRAGE_GAB', libelle: 'GAB, profils et BIN', icone: 'atm', chemin: '/parametrage/gab', lot: 5 },
    ],
  },
  {
    code: 'CHARGEMENT', libelle: 'Chargement des données', icone: 'upload_file', lot: 2,
    enfants: [
      { code: 'CHARGEMENT_LIS', libelle: 'Fichiers LIS', icone: 'drive_folder_upload', chemin: '/ingestion', lot: 2 },
      { code: 'CHARGEMENT_VSS', libelle: 'Fichiers VSS (VX)', icone: 'credit_card', chemin: '/ingestion/vss', lot: 3 },
      { code: 'CHARGEMENT_EJOURNAL', libelle: 'Journaux GAB', icone: 'receipt_long', chemin: '/ingestion/ejournal', lot: 4 },
    ],
  },
  {
    code: 'EXPLORATION', libelle: 'Exploration', icone: 'manage_search', lot: 2,
    enfants: [
      { code: 'EXPLORATION_LIS', libelle: 'Données LIS', icone: 'table_view', chemin: '/exploration', lot: 2 },
      { code: 'EXPLORATION_VSS', libelle: 'Rapports VSS', icone: 'summarize', chemin: '/exploration/vss', lot: 3 },
      { code: 'EXPLORATION_EJOURNAL', libelle: 'Journaux GAB', icone: 'local_atm', chemin: '/exploration/ejournal', lot: 4 },
    ],
  },
  {
    code: 'GAB', libelle: 'GAB : appros & arrêtés', icone: 'atm', lot: 5,
    enfants: [
      { code: 'GAB_APPRO', libelle: 'Approvisionnements', icone: 'add_card', chemin: '/gab/appros', lot: 5 },
      { code: 'GAB_ARRETE', libelle: 'Arrêtés', icone: 'point_of_sale', chemin: '/gab/arretes', lot: 5 },
      { code: 'GAB_VALIDATION', libelle: 'Validation (4 yeux)', icone: 'verified_user', chemin: '/gab/validation', lot: 5 },
    ],
  },
  {
    code: 'COMPENSE', libelle: 'Compense TPE', icone: 'storefront', lot: 9,
    enfants: [
      { code: 'COMPENSE_TRAITEMENT', libelle: 'Traitements de compense', icone: 'receipt', chemin: '/compense/traitements', lot: 9 },
      { code: 'COMPENSE_COMMERCANTS', libelle: 'Commerçants et comptes', icone: 'store', chemin: '/compense/commercants', lot: 9 },
      { code: 'COMPENSE_VALIDATION', libelle: 'Validation (4 yeux)', icone: 'verified_user', chemin: '/compense/validation', lot: 9 },
    ],
  },
  { code: 'RAPPROCHEMENT', libelle: 'Rapprochements', icone: 'compare_arrows', chemin: '/rapprochement', lot: 6 },
  {
    code: 'CHARGEBACK', libelle: 'Chargebacks', icone: 'currency_exchange', lot: 7,
    enfants: [
      { code: 'CHARGEBACK_EMISSION', libelle: 'Émission', icone: 'call_made', chemin: '/chargeback/emission', lot: 7 },
      { code: 'CHARGEBACK_ACQUISITION', libelle: 'Acquisition', icone: 'call_received', chemin: '/chargeback/acquisition', lot: 7 },
    ],
  },
  {
    code: 'REPORTING', libelle: 'Pilotage & réglementaire', icone: 'insights', lot: 8,
    enfants: [
      { code: 'REPORTING_KPI', libelle: 'Indicateurs (KPI)', icone: 'bar_chart', chemin: '/reporting/kpi', lot: 8 },
      { code: 'REPORTING_REGLEMENTAIRE', libelle: 'Réglementaire BCEAO / VISA', icone: 'account_balance', chemin: '/reporting/reglementaire', lot: 8 },
    ],
  },
];

/** Entrées pointant vers un écran (feuilles du menu). */
export function ecrans(menu: EntreeMenu[] = MENU): EntreeMenu[] {
  return menu.flatMap((e) => (e.enfants ? ecrans(e.enfants) : [e]));
}
