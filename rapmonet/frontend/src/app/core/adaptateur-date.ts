import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/**
 * Adaptateur de dates qui comprend la saisie au clavier au format français (JJ/MM/AAAA, JJ/MM/AA,
 * séparateurs « / », « . » ou « - »). L'adaptateur natif utilise Date.parse, qui lit 05/06 comme le 6 mai.
 */
@Injectable()
export class AdaptateurDateFr extends NativeDateAdapter {
  override parse(valeur: unknown, format?: object): Date | null {
    if (typeof valeur === 'string') {
      const m = /^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{2}|\d{4})\s*$/.exec(valeur);
      if (m) {
        const jour = +m[1];
        const mois = +m[2] - 1;
        const annee = m[3].length === 2 ? 2000 + +m[3] : +m[3];
        const d = new Date(annee, mois, jour);
        return d.getFullYear() === annee && d.getMonth() === mois && d.getDate() === jour ? d : this.invalid();
      }
      if (valeur.trim() === '') return null;
    }
    return super.parse(valeur, format);
  }
}
