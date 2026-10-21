import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';

/** Téléchargement des exports Excel / PDF générés par le serveur. */
@Injectable({ providedIn: 'root' })
export class Telechargement {
  private readonly http = inject(HttpClient);

  /** Télécharge le fichier ; en cas de refus, l'erreur porte le message du serveur. */
  telecharger(url: string, parametres: Record<string, string | number | undefined | null>): Observable<string> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(parametres)) {
      if (v !== undefined && v !== null && v !== '') params = params.set(k, String(v));
    }
    return this.http.get(url, { params, observe: 'response', responseType: 'blob' }).pipe(
      map((reponse) => {
        const nom = /filename="([^"]+)"/.exec(reponse.headers.get('Content-Disposition') ?? '')?.[1] ?? 'export';
        const lien = document.createElement('a');
        lien.href = URL.createObjectURL(reponse.body!);
        lien.download = nom;
        lien.click();
        setTimeout(() => URL.revokeObjectURL(lien.href), 1000);
        return nom;
      }),
      catchError((e: HttpErrorResponse) =>
        e.error instanceof Blob
          ? from(e.error.text()).pipe(switchMap((texte) => throwError(() => new Error(lireDetail(texte)))))
          : throwError(() => new Error('Export impossible.')),
      ),
    );
  }
}

function lireDetail(texte: string): string {
  try {
    return JSON.parse(texte).detail ?? 'Export impossible.';
  } catch {
    return 'Export impossible.';
  }
}
