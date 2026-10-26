import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { EditionTcr } from './edition-tcr';
import {
  Champ, FormatFichier, ModificationFormat, PADS, ParametrageService, SaisieChamp, TYPES_DONNEE, TcFormat,
} from './parametrage.service';

const FORMAT = 'LIS_GIM';

/** Paramétrage du fichier LIS : consultation, et modification motivée (champs, structure des TCR, TC) avec historique. */
@Component({
  selector: 'app-format-lis',
  imports: [DatePipe, FormsModule, EditionTcr, MatButtonModule, MatCardModule, MatCheckboxModule, MatExpansionModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, MatTableModule, MatTabsModule, MatTooltipModule],
  templateUrl: './format-lis.html',
  styleUrl: './format-lis.scss',
})
export class FormatLis {
  private readonly service = inject(ParametrageService);
  protected readonly peutModifier = inject(AuthService).peut('PARAMETRAGE_LIS');
  protected readonly types = TYPES_DONNEE;
  protected readonly pads = PADS;

  protected readonly format = signal<FormatFichier | null>(null);
  protected readonly champs = signal<Champ[]>([]);
  protected readonly historique = signal<ModificationFormat[]>([]);
  protected readonly filtre = signal('');
  protected readonly filtreChamps = signal('');
  protected readonly colonnes = ['position', 'taille', 'code', 'libelle', 'type', 'options'];
  protected readonly message = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);

  /** TCR en cours de modification : « TC/TCR ». */
  protected readonly tcrEdite = signal<string | null>(null);
  protected readonly tcEdite = signal<{ code: string; libelle: string; description: string; motif: string } | null>(null);
  protected readonly champEdite = signal<{ nouveau: boolean; champ: SaisieChamp; protege: boolean; confidentielAvant: boolean; motif: string } | null>(null);
  protected readonly nouveauTc = signal<{ code: string; libelle: string; description: string; tcrs: string; sousFichiers: string[]; motif: string } | null>(null);

  protected readonly tcs = computed<TcFormat[]>(() => {
    const f = this.format();
    if (!f) return [];
    const q = this.filtre().trim().toLowerCase();
    return Object.values(f.tcs)
      .filter((t) => !q || t.code.toLowerCase().includes(q) || t.libelle.toLowerCase().includes(q)
        || t.tcrs.some((r) => r.attributs.some((a) => a.code.toLowerCase().includes(q) || a.libelle.toLowerCase().includes(q))))
      .sort((a, b) => a.code.localeCompare(b.code));
  });

  protected readonly champsFiltres = computed(() => {
    const q = this.filtreChamps().trim().toLowerCase();
    return this.champs().filter((c) => !q || c.code.toLowerCase().includes(q) || c.libelle.toLowerCase().includes(q)).slice(0, 300);
  });

  constructor() {
    this.service.format(FORMAT).subscribe((f) => this.format.set(f));
    this.chargerChamps();
  }

  private chargerChamps(): void {
    this.service.champs(FORMAT).subscribe((c) => this.champs.set(c));
    this.service.historique(FORMAT).subscribe((h) => this.historique.set(h));
  }

  private apresModification(message: string): void {
    this.message.set(message);
    this.erreur.set(null);
    this.service.recharger(FORMAT).subscribe((f) => this.format.set(f));
    this.chargerChamps();
  }

  protected nbAttributs(t: TcFormat): number {
    return t.tcrs.reduce((n, r) => n + r.attributs.length, 0);
  }

  protected finEditionTcr(tc: string, tcr: string, enregistre: boolean): void {
    this.tcrEdite.set(null);
    if (enregistre) this.apresModification(`Structure du TC ${tc} / TCR ${tcr} enregistrée : elle s'applique aux prochains fichiers intégrés.`);
  }

  protected modifierTc(t: TcFormat): void {
    this.tcEdite.set({ code: t.code, libelle: t.libelle, description: t.description ?? '', motif: '' });
  }

  protected enregistrerTc(): void {
    const e = this.tcEdite();
    if (!e) return;
    this.service.modifierTc(FORMAT, e.code, e.libelle, e.description || null, e.motif).subscribe({
      next: () => { this.tcEdite.set(null); this.apresModification(`TC ${e.code} modifié.`); },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected ouvrirNouveauTc(): void {
    this.nouveauTc.set({ code: '', libelle: '', description: '', tcrs: '0', sousFichiers: [], motif: '' });
  }

  protected basculerSousFichier(code: string, actif: boolean): void {
    const n = this.nouveauTc();
    if (!n) return;
    n.sousFichiers = actif ? [...n.sousFichiers, code] : n.sousFichiers.filter((s) => s !== code);
  }

  protected creerTc(): void {
    const n = this.nouveauTc();
    if (!n) return;
    const tcrs = n.tcrs.split(/[\s,;]+/).filter((x) => x).map((code) => ({ code, libelle: null }));
    this.service.creerTc(FORMAT, { code: n.code, libelle: n.libelle, description: n.description || null, tcrs, sousFichiers: n.sousFichiers }, n.motif)
      .subscribe({
        next: () => { this.nouveauTc.set(null); this.apresModification(`TC ${n.code.toUpperCase()} créé : détaillez ses champs (« Modifier la structure »).`); },
        error: (x) => this.erreur.set(messageErreur(x)),
      });
  }

  protected modifierChamp(c: Champ): void {
    const { protege, utilisations, ...saisie } = c;
    this.champEdite.set({ nouveau: false, champ: { ...saisie }, protege, confidentielAvant: c.confidentiel, motif: '' });
  }

  protected ouvrirNouveauChamp(): void {
    this.champEdite.set({
      nouveau: true, protege: false, confidentielAvant: false, motif: '',
      champ: { code: 'LIS_', libelle: '', description: null, taille: 1, pad: 'ESPACE_DROITE', decimales: 0, type: 'ALPHA', specifique: true, confidentiel: false },
    });
  }

  protected enregistrerChamp(): void {
    const e = this.champEdite();
    if (!e) return;
    const appel = e.nouveau ? this.service.creerChamp(FORMAT, e.champ, e.motif) : this.service.modifierChamp(FORMAT, e.champ, e.motif);
    appel.subscribe({
      next: (c) => { this.champEdite.set(null); this.apresModification(`Champ ${c.code} ${e.nouveau ? 'créé' : 'modifié'}.`); },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected libelleAction(a: string): string {
    return ({ CHAMP_MODIFIE: 'Champ modifié', CHAMP_CREE: 'Champ créé', STRUCTURE_TCR: 'Structure de TCR', TC_MODIFIE: 'TC modifié', TC_CREE: 'TC créé' } as Record<string, string>)[a] ?? a;
  }
}
