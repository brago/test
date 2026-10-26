import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';
import { Champ, FormatFichier, ParametrageService, TcrFormat } from './parametrage.service';

interface Ligne { champ: Champ; debut: number; fin: number }

/**
 * Modification de la structure d'un TCR : champs ordonnés, positions recalculées, contrôle de la longueur.
 * Le test sur un fichier LIS se fait entièrement dans le navigateur : le fichier n'est pas envoyé au serveur
 * et les champs confidentiels sont masqués (6 premiers et 4 derniers chiffres).
 */
@Component({
  selector: 'app-edition-tcr',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatTooltipModule],
  templateUrl: './edition-tcr.html',
  styleUrl: './format-lis.scss',
})
export class EditionTcr implements OnInit {
  private readonly service = inject(ParametrageService);

  readonly format = input.required<FormatFichier>();
  readonly tc = input.required<string>();
  readonly tcr = input.required<TcrFormat>();
  readonly dictionnaire = input.required<Champ[]>();
  readonly termine = output<boolean>();

  protected readonly codes = signal<string[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly apercu = signal<{ fichier: string; lignes: string[][]; nb: number } | null>(null);
  protected readonly ajout = signal('');
  protected motif = '';
  private contenu: string[] = [];

  protected readonly capacite = computed(() => this.format().longueurEnregistrement - this.format().positionDonnees + 1);
  protected readonly lignes = computed<Ligne[]>(() => {
    const parCode = new Map(this.dictionnaire().map((c) => [c.code, c]));
    let p = this.format().positionDonnees;
    return this.codes().flatMap((code) => {
      const champ = parCode.get(code);
      if (!champ) return [];
      const l = { champ, debut: p, fin: p + champ.taille - 1 };
      p += champ.taille;
      return [l];
    });
  });
  protected readonly occupe = computed(() => this.lignes().reduce((n, l) => n + l.champ.taille, 0));
  /** Champs protégés de la structure d'origine qui n'y sont plus. */
  protected readonly protegesRetires = computed(() => {
    const parCode = new Map(this.dictionnaire().map((c) => [c.code, c]));
    return this.tcr().attributs.map((a) => a.code).filter((c) => parCode.get(c)?.protege && !this.codes().includes(c));
  });
  protected readonly modifie = computed(() => this.codes().join('|') !== this.tcr().attributs.map((a) => a.code).join('|'));
  protected readonly disponibles = computed(() => {
    const q = this.ajout().trim().toUpperCase();
    return this.dictionnaire().filter((c) => !this.codes().includes(c.code)
      && (!q || c.code.includes(q) || c.libelle.toUpperCase().includes(q))).slice(0, 50);
  });

  ngOnInit(): void {
    this.codes.set(this.tcr().attributs.map((a) => a.code));
  }

  protected deplacer(i: number, sens: number): void {
    const c = [...this.codes()];
    const j = i + sens;
    if (j < 0 || j >= c.length) return;
    [c[i], c[j]] = [c[j], c[i]];
    this.codes.set(c);
    this.relire();
  }

  protected retirer(i: number): void {
    this.codes.set(this.codes().filter((_, k) => k !== i));
    this.relire();
  }

  protected ajouter(code: string, apres: number | null = null): void {
    if (!this.dictionnaire().some((c) => c.code === code) || this.codes().includes(code)) return;
    const c = [...this.codes()];
    c.splice(apres === null ? c.length : apres + 1, 0, code);
    this.codes.set(c);
    this.ajout.set('');
    this.relire();
  }

  protected enregistrer(): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.service.modifierStructure(this.format().code, this.tc(), this.tcr().code, this.codes(), this.motif).subscribe({
      next: () => this.termine.emit(true),
      error: (e) => { this.erreur.set(messageErreur(e)); this.enCours.set(false); },
    });
  }

  /** Lit un fichier LIS local (sans l'envoyer) et décode les premières lignes de ce TC / TCR avec la structure proposée. */
  protected tester(evt: Event): void {
    const f = (evt.target as HTMLInputElement).files?.[0];
    if (!f) return;
    const lecteur = new FileReader();
    lecteur.onload = () => {
      const fmt = this.format();
      const zone = (l: string, z: { position: number; longueur: number }) => l.substring(z.position - 1, z.position - 1 + z.longueur).trim();
      // Numéro de TCR : comparaison numérique (« 0 », « 1 »…).
      this.contenu = String(lecteur.result).split(/\r?\n/)
        .filter((l) => zone(l, fmt.zoneTc) === this.tc() && Number(zone(l, fmt.zoneTcr)) === Number(this.tcr().code));
      this.relire(f.name);
    };
    lecteur.readAsText(f, 'ISO-8859-1');
    (evt.target as HTMLInputElement).value = '';
  }

  private relire(fichier = this.apercu()?.fichier): void {
    if (!fichier) return;
    const lignes = this.contenu.slice(0, 5).map((l) => this.lignes().map((x) => {
      const v = l.substring(x.debut - 1, x.fin).trim();
      return x.champ.confidentiel ? masquer(v) : v;
    }));
    this.apercu.set({ fichier, lignes, nb: this.contenu.length });
  }
}

/** 6 premiers et 4 derniers chiffres. */
function masquer(v: string): string {
  return v.length > 10 ? v.slice(0, 6) + '*'.repeat(v.length - 10) + v.slice(-4) : '*'.repeat(v.length);
}
