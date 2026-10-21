import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Session expirée ou compte désactivé : retour à l'écran de connexion. */
export const sessionInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return suivant(requete).pipe(
    catchError((e: HttpErrorResponse) => {
      if (e.status === 401 && !requete.url.startsWith('/api/auth/')) {
        auth.oublier();
        router.navigate(['/connexion'], { queryParams: { expiree: 1 } });
      }
      return throwError(() => e);
    }),
  );
};
