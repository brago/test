import { HttpErrorResponse } from '@angular/common/http';

/** Message lisible d'une erreur d'API (Problem Details). */
export function messageErreur(e: unknown, defaut = 'Une erreur est survenue.'): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 403) return 'Action non autorisée pour votre profil.';
    return e.error?.detail ?? defaut;
  }
  return e instanceof Error ? e.message : defaut;
}
