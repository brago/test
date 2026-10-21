/**
 * Menu principal de RAPMONET : un groupe par module fonctionnel.
 * Les entrées sans écran réalisé pointent vers la page « À venir ».
 */
export interface EntreeMenu {
  libelle: string;
  icone: string;
  chemin: string;
  lot: number;
}

export const MENU: EntreeMenu[] = [
  { libelle: 'Accueil', icone: 'home', chemin: '/accueil', lot: 0 },
  { libelle: 'Sécurité & habilitations', icone: 'admin_panel_settings', chemin: '/securite', lot: 1 },
  { libelle: 'Paramétrage', icone: 'tune', chemin: '/parametrage', lot: 2 },
  { libelle: 'Chargement des données', icone: 'upload_file', chemin: '/ingestion', lot: 2 },
  { libelle: 'Exploration', icone: 'manage_search', chemin: '/exploration', lot: 2 },
  { libelle: 'GAB : appros & arrêtés', icone: 'atm', chemin: '/gab', lot: 5 },
  { libelle: 'Rapprochements', icone: 'compare_arrows', chemin: '/rapprochement', lot: 6 },
  { libelle: 'Chargebacks', icone: 'currency_exchange', chemin: '/chargeback', lot: 7 },
  { libelle: 'Pilotage & réglementaire', icone: 'insights', chemin: '/reporting', lot: 8 },
];
