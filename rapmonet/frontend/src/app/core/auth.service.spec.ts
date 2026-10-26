import { UtilisateurConnecte, etapeRequise, sessionComplete } from './auth.service';

const u = (p: Partial<UtilisateurConnecte> = {}): UtilisateurConnecte => ({
  login: 'agent', nom: 'Agent', agence: null, profil: 'P', libelleProfil: 'P', menus: [], doitChangerMotDePasse: false,
  deuxFacteurs: false, doitActiverDeuxFacteurs: false, expirationMotDePasse: null, ...p,
});

describe('Session limitée', () => {
  it('exige d\'abord le changement de mot de passe, puis l\'activation de la double authentification', () => {
    expect(etapeRequise(u({ doitChangerMotDePasse: true, doitActiverDeuxFacteurs: true }))).toBe('/mot-de-passe');
    expect(etapeRequise(u({ doitActiverDeuxFacteurs: true }))).toBe('/deux-facteurs');
    expect(etapeRequise(u())).toBeNull();
  });

  it('n\'est complète qu\'une fois les deux étapes faites', () => {
    expect(sessionComplete(null)).toBe(false);
    expect(sessionComplete(u({ doitActiverDeuxFacteurs: true }))).toBe(false);
    expect(sessionComplete(u({ deuxFacteurs: true }))).toBe(true);
  });
});
