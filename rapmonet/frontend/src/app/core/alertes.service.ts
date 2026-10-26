import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

export type NiveauAlerte = 'CRITIQUE' | 'ATTENTION' | 'INFO';

export interface Alerte {
  code: string; niveau: NiveauAlerte; titre: string; detail: string; nombre: number; montant: number | null;
  menu: string; lien: string;
}

/** Alertes de l'utilisateur connecté (filtrées par le serveur sur ses menus), partagées entre l'en-tête et l'accueil. */
@Injectable({ providedIn: 'root' })
export class AlertesService {
  private readonly http = inject(HttpClient);
  readonly alertes = signal<Alerte[] | null>(null);
  readonly erreur = signal(false);
  /** Nombre d'alertes critiques ou à traiter dans la journée (pastille de l'en-tête). */
  readonly aTraiter = computed(() => (this.alertes() ?? []).filter((a) => a.niveau !== 'INFO').length);

  rafraichir(): void {
    this.http.get<Alerte[]>('/api/alertes').subscribe({
      next: (a) => { this.alertes.set(a); this.erreur.set(false); },
      error: () => this.erreur.set(true),
    });
  }

  vider(): void {
    this.alertes.set(null);
  }
}
