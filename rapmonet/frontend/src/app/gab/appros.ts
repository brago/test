import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { Appro, Cassette, dateHeureIso, Gab, GabService, LIBELLES_STATUT_GAB, PassageJournal } from './gab.service';

interface Formulaire {
  id: number | null; terminal: string; jour: Date | null; heure: string; gabiste: string; referenceExterne: string;
  numeroBon: string; commentaire: string; billets: Record<string, number>;
}

/** Approvisionnements GAB : saisie du billetage, contrôle avec le journal, validation à quatre yeux, rapport. */
@Component({
  selector: 'app-appros',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatDatepickerModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatPaginatorModule, MatSelectModule, MatTableModule, MatTooltipModule],
  templateUrl: './appros.html',
  styleUrl: './gab.scss',
})
export class Appros {
  private readonly service = inject(GabService);
  private readonly telechargement = inject(Telechargement);
  private readonly auth = inject(AuthService);
  protected readonly peutSaisir = this.auth.peut('GAB_APPRO');
  protected readonly peutValider = this.auth.peut('GAB_VALIDATION');
  protected readonly moi = this.auth.utilisateur;
  protected readonly statuts = LIBELLES_STATUT_GAB;

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected terminal = '';
  protected statut = '';

  protected readonly gabs = signal<Gab[]>([]);
  protected readonly lignes = signal<Appro[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly taille = signal(25);
  protected readonly selection = signal<Appro | null>(null);
  protected readonly formulaire = signal<Formulaire | null>(null);
  protected readonly cassettes = signal<Cassette[]>([]);
  protected readonly passages = signal<PassageJournal[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  /** Incrémenté à chaque saisie pour recalculer les totaux (le formulaire est muté par ngModel). */
  protected readonly saisie = signal(0);
  /** Numéro de la dernière demande de préparation : les réponses plus anciennes sont ignorées. */
  private jeton = 0;
  protected readonly colonnes = ['reference', 'dateAppro', 'terminal', 'gabiste', 'montant', 'controle', 'statut', 'arrete'];

  protected readonly totalSaisi = computed(() => {
    this.saisie();
    const f = this.formulaire();
    return f ? this.cassettes().reduce((s, c) => s + (f.billets[c.cassette] || 0) * c.coupure, 0) : 0;
  });

  /** Billets saisis regroupés par type de billet NCR (1 à 4), comparables au journal. */
  protected readonly saisiParType = computed(() => {
    this.saisie();
    const f = this.formulaire();
    const t = [0, 0, 0, 0];
    if (f) this.cassettes().forEach((c) => (t[c.type - 1] += f.billets[c.cassette] || 0));
    return t;
  });

  constructor() {
    this.service.gabs().subscribe((g) => this.gabs.set(g));
    const id = inject(ActivatedRoute).snapshot.queryParamMap.get('id');
    if (id) this.ouvrir(+id);
    this.rechercher();
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.service.appros(this.critere(), page, this.taille()).subscribe((r) => {
      this.lignes.set(r.elements);
      this.total.set(r.total);
    });
  }

  protected changerPage(e: PageEvent): void {
    this.taille.set(e.pageSize);
    this.rechercher(e.pageIndex);
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.telechargement.telecharger('/api/gab/appros/export', { format, ...this.critere() })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  protected ouvrir(id: number): void {
    this.formulaire.set(null);
    this.service.appro(id).subscribe((a) => this.selection.set(a));
  }

  protected rapport(a: Appro): void {
    this.telechargement.telecharger(`/api/gab/appros/${a.id}/rapport`, {})
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  protected nouveau(): void {
    const maintenant = new Date();
    const heure = `${String(maintenant.getHours()).padStart(2, '0')}:${String(maintenant.getMinutes()).padStart(2, '0')}`;
    this.editer({ id: null, terminal: this.terminal || '', jour: maintenant, heure, gabiste: '', referenceExterne: '',
      numeroBon: '', commentaire: '', billets: {} });
  }

  protected modifier(a: Appro): void {
    const d = new Date(a.dateAppro);
    this.editer({ id: a.id, terminal: a.terminal, jour: d, heure: a.dateAppro.substring(11, 16), gabiste: a.gabiste,
      referenceExterne: a.referenceExterne ?? '', numeroBon: a.numeroBon ?? '', commentaire: a.commentaire ?? '',
      billets: Object.fromEntries(a.billetage.map((b) => [b.cassette, b.nombre])) });
  }

  private editer(f: Formulaire): void {
    this.selection.set(null);
    this.message.set(null);
    this.erreur.set(null);
    this.formulaire.set(f);
    this.preparer();
  }

  /** Cassettes du GAB choisi et passages du gabiste relevés dans le journal ce jour-là. */
  protected preparer(): void {
    const f = this.formulaire();
    if (!f?.terminal || !f.jour) {
      this.cassettes.set([]);
      this.passages.set([]);
      return;
    }
    const jeton = ++this.jeton;
    this.service.preparationAppro(f.terminal, dateIso(f.jour)!).subscribe({
      next: (p) => {
        if (jeton !== this.jeton) return; // réponse d'une demande plus ancienne (GAB ou date changés depuis)
        this.cassettes.set(p.cassettes);
        this.passages.set(p.passages);
        this.saisie.update((n) => n + 1);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  /** Reprend les billets ajoutés selon le journal, quand le type n'est porté que par une cassette. */
  protected reprendre(p: PassageJournal): void {
    const f = this.formulaire();
    if (!f || !p.ajoutes) return;
    this.cassettes().forEach((c) => {
      if (this.cassettesDuType(c.type).length === 1) f.billets[c.cassette] = p.ajoutes![c.type - 1];
    });
    f.heure = p.dateHeure.substring(11, 16);
    this.saisie.update((n) => n + 1);
  }

  protected cassettesDuType(type: number): string[] {
    return this.cassettes().filter((c) => c.type === type).map((c) => c.cassette);
  }

  protected types(): number[] {
    return [...new Set(this.cassettes().map((c) => c.type))].sort();
  }

  protected coupureDuType(type: number): number {
    return this.cassettes().find((c) => c.type === type)?.coupure ?? 0;
  }

  protected enregistrer(): void {
    const f = this.formulaire();
    if (!f) return;
    this.erreur.set(null);
    const s = { terminal: f.terminal, dateAppro: dateHeureIso(f.jour, f.heure), gabiste: f.gabiste,
      referenceExterne: f.referenceExterne, numeroBon: f.numeroBon, commentaire: f.commentaire, billets: f.billets };
    (f.id ? this.service.modifierAppro(f.id, s) : this.service.creerAppro(s)).subscribe({
      next: (a) => {
        this.formulaire.set(null);
        this.selection.set(a);
        this.message.set(`Appro ${a.reference} enregistré : il doit maintenant être validé par une autre personne.`);
        this.rechercher(this.page());
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected peutDecider(a: Appro): boolean {
    const login = this.moi()?.login;
    return this.peutValider && a.statut === 'SAISI' && login !== a.saisiPar && login !== a.modifiePar;
  }

  protected decider(a: Appro, decision: 'validation' | 'annulation'): void {
    let motif: string | undefined;
    if (decision === 'annulation') {
      motif = prompt(`Motif de l'annulation de l'appro ${a.reference} :`) ?? undefined;
      if (!motif) return;
    }
    this.erreur.set(null);
    this.service.decider('appros', a.id, decision, motif).subscribe({
      next: () => {
        this.message.set(`Appro ${a.reference} ${decision === 'validation' ? 'validé' : 'annulé'}.`);
        this.ouvrir(a.id);
        this.rechercher(this.page());
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected modifie(): void {
    this.saisie.update((n) => n + 1);
  }

  private critere() {
    return { du: dateIso(this.du), au: dateIso(this.au), terminal: this.terminal, statut: this.statut };
  }
}
