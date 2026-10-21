import { Component, computed, input, signal } from '@angular/core';

export interface PointGraphique { libelle: string; valeur: number; detail?: string; }

const FMT = new Intl.NumberFormat('fr-FR');

/** Valeur abrégée pour les axes : 40 M, 950 k. */
function abrege(v: number): string {
  const a = Math.abs(v);
  if (a >= 1e9) return `${FMT.format(+(v / 1e9).toFixed(1))} Md`;
  if (a >= 1e6) return `${FMT.format(+(v / 1e6).toFixed(1))} M`;
  if (a >= 1e3) return `${FMT.format(+(v / 1e3).toFixed(1))} k`;
  return FMT.format(v);
}

/** Maximum « rond » et graduations de l'axe. */
function graduations(max: number): number[] {
  if (max <= 0) return [0, 1];
  const brut = max / 4;
  const p = 10 ** Math.floor(Math.log10(brut));
  const pas = [1, 2, 2.5, 5, 10].map((m) => m * p).find((x) => x >= brut)!;
  const n = Math.ceil(max / pas);
  return Array.from({ length: n + 1 }, (_, i) => i * pas);
}

/**
 * Histogramme d'une seule série (pas de légende : le titre la nomme). Colonnes verticales (par mois) ou
 * barres horizontales (classement). Barres fines à extrémité arrondie, grille discrète, infobulle au survol,
 * étiquette sur la plus grande valeur seulement.
 */
@Component({
  selector: 'app-graphique-barres',
  template: `
    <div class="graphique" (mouseleave)="survol.set(null)">
      <svg [attr.viewBox]="'0 0 ' + largeur + ' ' + hauteur()" role="img" [attr.aria-label]="titre()">
        @if (orientation() === 'colonnes') {
          @for (g of axe(); track g.v) {
            <line class="grille" [attr.x1]="gauche" [attr.x2]="largeur - 8" [attr.y1]="y(g.v)" [attr.y2]="y(g.v)" />
            <text class="axe" [attr.x]="gauche - 8" [attr.y]="y(g.v) + 4" text-anchor="end">{{ g.libelle }}</text>
          }
          @for (p of points(); track $index) {
            <path class="barre" [class.estompee]="survol() !== null && survol() !== $index" [attr.d]="colonne($index, p.valeur)" />
            <text class="axe" [attr.x]="centre($index)" [attr.y]="hauteur() - 8" text-anchor="middle">{{ p.libelle }}</text>
            @if ($index === indiceMax() && p.valeur > 0) {
              <text class="valeur" [attr.x]="centre($index)" [attr.y]="y(p.valeur) - 6" text-anchor="middle">{{ court(p.valeur) }}</text>
            }
            <rect class="cible" [attr.x]="centre($index) - bande() / 2" [attr.y]="haut" [attr.width]="bande()"
                  [attr.height]="hauteur() - haut - bas" (mouseenter)="survol.set($index)" />
          }
        } @else {
          @for (p of points(); track $index) {
            <text class="axe" [attr.x]="etiquettes - 8" [attr.y]="ligneY($index) + 4" text-anchor="end">{{ tronque(p.libelle) }}</text>
            <path class="barre" [class.estompee]="survol() !== null && survol() !== $index" [attr.d]="barre($index, p.valeur)" />
            <text class="valeur" [attr.x]="etiquettes + longueur(p.valeur) + 6" [attr.y]="ligneY($index) + 4">{{ court(p.valeur) }}</text>
            <rect class="cible" x="0" [attr.y]="ligneY($index) - pasLigne / 2" [attr.width]="largeur" [attr.height]="pasLigne"
                  (mouseenter)="survol.set($index)" />
          }
        }
      </svg>
      @if (survol() !== null && points()[survol()!]; as p) {
        <div class="infobulle" [style.left.%]="positionInfobulle()">
          <strong>{{ p.libelle }}</strong><br />{{ format(p.valeur) }} {{ unite() }}
          @if (p.detail) { <br /><span>{{ p.detail }}</span> }
        </div>
      }
    </div>`,
  styles: `
    :host { display: block; --serie-1: #2a78d6; --grille: #e6e5e1; --texte-secondaire: #52514e; --texte: #0b0b0b; }
    .graphique { position: relative; }
    svg { width: 100%; height: auto; display: block; font-family: inherit; }
    .grille { stroke: var(--grille); stroke-width: 1; }
    .axe { fill: var(--texte-secondaire); font-size: 14px; }
    .valeur { fill: var(--texte); font-size: 14px; font-weight: 500; }
    .barre { fill: var(--serie-1); transition: opacity .15s; }
    .barre.estompee { opacity: .45; }
    .cible { fill: transparent; cursor: default; }
    .infobulle { position: absolute; top: 4px; transform: translateX(-50%); background: #fff; color: var(--texte);
      border: 1px solid var(--grille); border-radius: 6px; padding: 6px 10px; font-size: 12px; pointer-events: none;
      box-shadow: 0 2px 8px rgba(0,0,0,.12); white-space: nowrap; }
    .infobulle span { color: var(--texte-secondaire); }
  `,
})
export class GraphiqueBarres {
  readonly points = input.required<PointGraphique[]>();
  readonly orientation = input<'colonnes' | 'barres'>('colonnes');
  readonly unite = input('');
  readonly titre = input('');

  protected readonly survol = signal<number | null>(null);
  protected readonly largeur = 720;
  protected readonly gauche = 72;
  protected readonly haut = 16;
  protected readonly bas = 28;
  protected readonly etiquettes = 230;
  protected readonly pasLigne = 34;

  protected readonly hauteur = computed(() =>
    this.orientation() === 'colonnes' ? 240 : this.points().length * this.pasLigne + 8);
  private readonly graduations = computed(() => graduations(Math.max(0, ...this.points().map((p) => p.valeur))));
  private readonly max = computed(() => this.graduations().at(-1) || 1);
  protected readonly axe = computed(() => this.graduations().map((v) => ({ v, libelle: abrege(v) })));
  protected readonly indiceMax = computed(() => {
    const v = this.points().map((p) => p.valeur);
    return v.indexOf(Math.max(...v));
  });
  protected readonly bande = computed(() => (this.largeur - 8 - this.gauche) / Math.max(1, this.points().length));

  protected y(v: number): number {
    return this.haut + (this.hauteur() - this.haut - this.bas) * (1 - v / this.max());
  }

  protected centre(i: number): number {
    return this.gauche + this.bande() * (i + 0.5);
  }

  /** Colonne : base carrée sur l'axe, sommet arrondi (4 px). */
  protected colonne(i: number, v: number): string {
    const l = Math.min(24, this.bande() * 0.6);
    const x = this.centre(i) - l / 2;
    const y0 = this.y(0);
    const y1 = this.y(v);
    const r = Math.min(4, (y0 - y1) / 2, l / 2);
    if (y0 - y1 < 0.5) return '';
    return `M${x},${y0} V${y1 + r} Q${x},${y1} ${x + r},${y1} H${x + l - r} Q${x + l},${y1} ${x + l},${y1 + r} V${y0} Z`;
  }

  protected ligneY(i: number): number {
    return i * this.pasLigne + this.pasLigne / 2 + 4;
  }

  protected longueur(v: number): number {
    const max = Math.max(1, ...this.points().map((p) => p.valeur));
    return (this.largeur - this.etiquettes - 90) * (v / max);
  }

  /** Barre horizontale : base carrée à gauche, extrémité arrondie (4 px). */
  protected barre(i: number, v: number): string {
    const h = Math.min(18, this.pasLigne * 0.6);
    const x0 = this.etiquettes;
    const x1 = x0 + this.longueur(v);
    const y = this.ligneY(i) - h / 2;
    const r = Math.min(4, (x1 - x0) / 2, h / 2);
    if (x1 - x0 < 0.5) return '';
    return `M${x0},${y} H${x1 - r} Q${x1},${y} ${x1},${y + r} V${y + h - r} Q${x1},${y + h} ${x1 - r},${y + h} H${x0} Z`;
  }

  protected positionInfobulle(): number {
    const i = this.survol() ?? 0;
    if (this.orientation() === 'barres') return 60;
    return Math.min(85, Math.max(15, (this.centre(i) / this.largeur) * 100));
  }

  protected court(v: number): string {
    return abrege(v);
  }

  protected format(v: number): string {
    return FMT.format(v);
  }

  protected tronque(s: string): string {
    return s.length > 30 ? s.substring(0, 29) + '…' : s;
  }
}
