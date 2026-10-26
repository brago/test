import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { messageErreur } from '../core/erreurs';
import { GraphiqueBarres, PointGraphique } from './graphique-barres';

interface Indicateur {
  libelle: string; unite: 'NOMBRE' | 'XOF' | 'POURCENT' | 'JOURS'; valeur: number; precedent: number | null;
  variation: number | null; favorable: 'HAUSSE' | 'BAISSE' | 'NEUTRE'; detail: string | null;
}
interface Tableau {
  mois: string; moisPrecedent: string; dernierMoisAvecDonnees: string | null;
  blocs: { titre: string; icone: string; lien: string; indicateurs: Indicateur[] }[];
  pointsAttention: { niveau: string; texte: string; lien: string }[];
  tendance: { mois: string; nombre: number; montant: number }[];
  alertes: Partial<Record<string, number>>;
}

const MOIS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

/** Tableau de bord de la direction : le mois choisi comparé au mois précédent, tendance, points d'attention. */
@Component({
  selector: 'app-direction',
  imports: [FormsModule, RouterLink, GraphiqueBarres, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule],
  templateUrl: './direction.html',
  styleUrl: './direction.scss',
})
export class Direction {
  private readonly http = inject(HttpClient);

  protected readonly choixMois: { valeur: string; libelle: string }[] = [];
  protected mois = '';
  protected readonly tableau = signal<Tableau | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly edition = new Date();

  protected readonly tendance = computed<PointGraphique[]>(() => (this.tableau()?.tendance ?? []).map((p) => ({
    libelle: MOIS[Number(p.mois.slice(5)) - 1].slice(0, 4) + '.', valeur: p.montant,
    detail: `${this.libelleMois(p.mois)} : ${p.nombre.toLocaleString('fr-FR')} retrait(s)`,
  })));

  constructor() {
    const d = new Date();
    for (let i = 0; i < 24; i++) {
      const m = new Date(d.getFullYear(), d.getMonth() - i, 1);
      const v = `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}`;
      this.choixMois.push({ valeur: v, libelle: this.libelleMois(v) });
    }
    this.mois = this.choixMois[0].valeur;
    this.charger(true);
  }

  /** Au premier affichage, se place sur le dernier mois qui a des données si le mois en cours est vide. */
  protected charger(premier = false): void {
    this.erreur.set(null);
    this.http.get<Tableau>('/api/reporting/direction', { params: { mois: this.mois } }).subscribe({
      next: (t) => {
        const vide = t.blocs[0].indicateurs[0].valeur === 0;
        if (premier && vide && t.dernierMoisAvecDonnees && t.dernierMoisAvecDonnees !== this.mois) {
          this.mois = t.dernierMoisAvecDonnees;
          this.charger();
          return;
        }
        this.tableau.set(t);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected libelleMois(v: string): string {
    return `${MOIS[Number(v.slice(5)) - 1]} ${v.slice(0, 4)}`;
  }

  protected valeur(i: Indicateur): string {
    const n = i.valeur.toLocaleString('fr-FR', { maximumFractionDigits: 1 });
    return i.unite === 'XOF' ? n + ' XOF' : i.unite === 'POURCENT' ? n + ' %' : i.unite === 'JOURS' ? n + ' j' : n;
  }

  protected precedent(i: Indicateur): string {
    if (i.precedent === null) return '';
    const n = i.precedent.toLocaleString('fr-FR', { maximumFractionDigits: 1 });
    return i.unite === 'XOF' ? n + ' XOF' : i.unite === 'POURCENT' ? n + ' %' : i.unite === 'JOURS' ? n + ' j' : n;
  }

  protected evolution(i: Indicateur): string {
    if (i.variation === null) return i.precedent === null || i.valeur === 0 ? '' : 'nouveau';
    const s = (i.variation > 0 ? '+' : i.variation < 0 ? '−' : '') + Math.abs(i.variation).toLocaleString('fr-FR', { maximumFractionDigits: 1 });
    return i.unite === 'POURCENT' ? `${s} pt` : i.unite === 'JOURS' ? `${s} j` : `${s} %`;
  }

  /** Vert si l'évolution va dans le bon sens, orange sinon, gris si neutre ou stable. */
  protected tonalite(i: Indicateur): string {
    if (!i.variation || i.favorable === 'NEUTRE') return 'neutre';
    return (i.variation > 0) === (i.favorable === 'HAUSSE') ? 'bon' : 'mauvais';
  }

  protected imprimer(): void {
    this.http.post(`/api/reporting/direction/impression`, null, { params: { mois: this.mois } }).subscribe();
    setTimeout(() => window.print(), 100);
  }
}
