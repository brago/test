import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';

export interface ParametreApplication {
  cle: string; groupe: string; libelle: string; description: string | null;
  type: 'TEXTE' | 'ENTIER' | 'HEURE' | 'FORMAT_DATE' | 'LISTE_ENTIERS' | 'BOOLEEN';
  min: number | null; max: number | null; valeur: string; modifiable: boolean; gerePar: string | null; lienEcran: string | null;
  exemple: string | null; modifiePar: string | null; modifieLe: string | null;
}
export interface ModificationParametre { id: number; cle: string; avant: string | null; apres: string; motif: string; modifiePar: string | null; modifieLe: string }

const API = '/api/parametrage/application';

/** Vue d'ensemble des paramètres de l'application ; modification motivée de ceux qui n'ont pas d'écran dédié. */
@Component({
  selector: 'app-parametres-application',
  imports: [DatePipe, FormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatTooltipModule],
  templateUrl: './parametres-application.html',
  styleUrl: './format-lis.scss',
})
export class ParametresApplication {
  private readonly http = inject(HttpClient);

  protected readonly parametres = signal<ParametreApplication[]>([]);
  protected readonly historique = signal<ModificationParametre[]>([]);
  protected readonly edition = signal<{ cle: string; valeur: string; motif: string } | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  /** Groupes modifiables d'abord, puis groupes gérés par d'autres écrans. */
  protected readonly groupes = computed(() => {
    const m = new Map<string, ParametreApplication[]>();
    for (const p of this.parametres()) m.set(p.groupe, [...(m.get(p.groupe) ?? []), p]);
    return [...m.entries()].map(([nom, params]) => ({
      nom, params, modifiable: params.some((p) => p.modifiable),
      ecran: params.find((p) => p.lienEcran) ?? null,
    })).sort((a, b) => Number(b.modifiable) - Number(a.modifiable));
  });

  constructor() {
    this.charger();
  }

  private charger(): void {
    this.http.get<ParametreApplication[]>(API).subscribe({
      next: (p) => this.parametres.set(p), error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.http.get<ModificationParametre[]>(`${API}/historique`).subscribe((h) => this.historique.set(h));
  }

  protected modifier(p: ParametreApplication): void {
    this.erreur.set(null);
    this.edition.set({ cle: p.cle, valeur: p.valeur, motif: '' });
  }

  protected enregistrer(p: ParametreApplication): void {
    const e = this.edition();
    if (!e) return;
    this.http.put<ParametreApplication>(`${API}/${encodeURIComponent(p.cle)}`, { valeur: String(e.valeur), motif: e.motif }).subscribe({
      next: (r) => {
        this.edition.set(null);
        this.erreur.set(null);
        this.message.set(`« ${r.libelle} » enregistré : ${r.valeur}.`);
        this.charger();
      },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected aide(p: ParametreApplication): string {
    switch (p.type) {
      case 'ENTIER': return `${p.min} à ${p.max}`;
      case 'LISTE_ENTIERS': return '4 valeurs : types 1 à 4, 0 = non chargé';
      case 'FORMAT_DATE': return 'ex. yy/MM/dd, dd/MM/yy';
      case 'HEURE': return 'HH:mm';
      default: return '';
    }
  }

  protected libelle(cle: string): string {
    return this.parametres().find((p) => p.cle === cle)?.libelle ?? cle;
  }
}
