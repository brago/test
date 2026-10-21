import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/** Exige une session complète ; sinon renvoie vers la connexion ou le changement de mot de passe. */
export const sessionRequise: CanActivateFn = (route, etat) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.charger().pipe(
    map((u) => {
      if (!u) return router.createUrlTree(['/connexion'], { queryParams: { retour: etat.url } });
      if (u.doitChangerMotDePasse) return router.createUrlTree(['/mot-de-passe']);
      const menu = route.data['menu'] as string | undefined;
      if (menu && !u.menus.includes(menu)) return router.createUrlTree(['/accueil']);
      return true;
    }),
  );
};

/** Écran de changement de mot de passe : accessible dès qu'une session existe. */
export const sessionOuverte: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).charger().pipe(map((u) => (u ? true : router.createUrlTree(['/connexion']))));
};
