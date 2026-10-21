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
    ],
  },
  {
    code: 'CHARGEMENT', libelle: 'Chargement des données', icone: 'upload_file', lot: 2,
    enfants: [
      { code: 'CHARGEMENT_LIS', libelle: 'Fichiers LIS', icone: 'drive_folder_upload', chemin: '/ingestion', lot: 2 },
      { code: 'CHARGEMENT_VSS', libelle: 'Fichiers VSS (VX)', icone: 'credit_card', chemin: '/ingestion/vss', lot: 3 },
    ],
  },
  {
    code: 'EXPLORATION', libelle: 'Exploration', icone: 'manage_search', lot: 2,
    enfants: [
      { code: 'EXPLORATION_LIS', libelle: 'Données LIS', icone: 'table_view', chemin: '/exploration', lot: 2 },
      { code: 'EXPLORATION_VSS', libelle: 'Rapports VSS', icone: 'summarize', chemin: '/exploration/vss', lot: 3 },
    ],
  },
  { code: 'GAB', libelle: 'GAB : appros & arrêtés', icone: 'atm', chemin: '/gab', lot: 5 },
  { code: 'RAPPROCHEMENT', libelle: 'Rapprochements', icone: 'compare_arrows', chemin: '/rapprochement', lot: 6 },
  { code: 'CHARGEBACK', libelle: 'Chargebacks', icone: 'currency_exchange', chemin: '/chargeback', lot: 7 },
  { code: 'REPORTING', libelle: 'Pilotage & réglementaire', icone: 'insights', chemin: '/reporting', lot: 8 },
];

/** Entrées pointant vers un écran (feuilles du menu). */
export function ecrans(menu: EntreeMenu[] = MENU): EntreeMenu[] {
  return menu.flatMap((e) => (e.enfants ? ecrans(e.enfants) : [e]));
}
