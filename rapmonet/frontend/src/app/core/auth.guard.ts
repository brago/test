import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService, etapeRequise } from './auth.service';

/** Exige une session complète ; sinon renvoie vers la connexion, le changement de mot de passe ou l'activation de la double authentification. */
export const sessionRequise: CanActivateFn = (route, etat) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.charger().pipe(
    map((u) => {
      if (!u) return router.createUrlTree(['/connexion'], { queryParams: { retour: etat.url } });
      const etape = etapeRequise(u);
      if (etape) return router.createUrlTree([etape]);
      const menu = route.data['menu'] as string | undefined;
      if (menu && !u.menus.includes(menu)) return router.createUrlTree(['/accueil']);
      return true;
    }),
  );
};

/** Écrans du compte (mot de passe, double authentification) : accessibles dès qu'une session existe. */
export const sessionOuverte: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).charger().pipe(map((u) => (u ? true : router.createUrlTree(['/connexion']))));
};
