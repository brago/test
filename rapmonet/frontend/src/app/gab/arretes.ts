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
import { Arrete, Compte, dateHeureIso, Gab, GabService, LIBELLES_STATUT_GAB, PreparationArrete } from './gab.service';

interface Formulaire {
  id: number | null; terminal: string; approId: number | null; jour: Date | null; heure: string; gabiste: string;
  commentaire: string; journal: Record<number, Compte>; inventaire: Record<string, Compte>; transactionsAttente: number;
  soldeComptable: number | null;
}

/** Arrêtés GAB : compteurs du journal, inventaire physique, écarts, validation à quatre yeux, rapport. */
@Component({
  selector: 'app-arretes',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatDatepickerModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatPaginatorModule, MatSelectModule, MatTableModule, MatTooltipModule],
  templateUrl: './arretes.html',
  styleUrl: './gab.scss',
})
export class Arretes {
  private readonly service = inject(GabService);
  private readonly telechargement = inject(Telechargement);
  private readonly auth = inject(AuthService);
  protected readonly peutSaisir = this.auth.peut('GAB_ARRETE');
  protected readonly peutValider = this.auth.peut('GAB_VALIDATION');
  protected readonly moi = this.auth.utilisateur;
  protected readonly statuts = LIBELLES_STATUT_GAB;

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected terminal = '';
  protected statut = '';

  protected readonly gabs = signal<Gab[]>([]);
  protected readonly lignes = signal<Arrete[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly taille = signal(25);
  protected readonly selection = signal<Arrete | null>(null);
  protected readonly formulaire = signal<Formulaire | null>(null);
  protected readonly preparation = signal<PreparationArrete | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly saisie = signal(0);
  /** Numéro de la dernière demande de préparation : les réponses plus anciennes sont ignorées. */
  private jeton = 0;
  protected readonly colonnes = ['reference', 'dateArrete', 'terminal', 'appro', 'journal', 'physique', 'ecart', 'ecartComptable', 'statut'];

  /** Types de billets chargés (d'après les cassettes du profil du GAB). */
  protected readonly types = computed(() =>
    [...new Set((this.preparation()?.cassettes ?? []).map((c) => c.type))].sort());

  protected readonly calcul = computed(() => {
    this.saisie();
    const f = this.formulaire();
    const p = this.preparation();
    if (!f || !p) return null;
    const coupure = (t: number) => p.coupures[t - 1] ?? 0;
    const journal = this.types().reduce((s, t) => s + ((f.journal[t]?.nombre || 0) + (f.journal[t]?.rejet || 0)) * coupure(t), 0);
    const physique = p.cassettes.reduce((s, c) => s + ((f.inventaire[c.cassette]?.nombre || 0) + (f.inventaire[c.cassette]?.rejet || 0)) * c.coupure, 0);
    const appro = p.approsNonArretes.find((a) => a.id === f.approId)?.montant ?? null;
    const comptable = f.soldeComptable;
    return {
      appro, journal, physique, ecart: physique - journal,
      ecartComptable: comptable === null || comptable === undefined || (comptable as unknown) === '' ? null
        : physique + (f.transactionsAttente || 0) - comptable,
      theorique: appro !== null && p.retraitsJournal !== null ? appro - p.retraitsJournal : null,
    };
  });

  constructor() {
    this.service.gabs().subscribe((g) => this.gabs.set(g));
    const id = inject(ActivatedRoute).snapshot.queryParamMap.get('id');
    if (id) this.ouvrir(+id);
    this.rechercher();
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.service.arretes(this.critere(), page, this.taille()).subscribe((r) => {
      this.lignes.set(r.elements);
      this.total.set(r.total);
    });
  }

  protected changerPage(e: PageEvent): void {
    this.taille.set(e.pageSize);
    this.rechercher(e.pageIndex);
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.telechargement.telecharger('/api/gab/arretes/export', { format, ...this.critere() })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  protected ouvrir(id: number): void {
    this.formulaire.set(null);
    this.service.arrete(id).subscribe((a) => this.selection.set(a));
  }

  protected rapport(a: Arrete): void {
    this.telechargement.telecharger(`/api/gab/arretes/${a.id}/rapport`, {})
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }

  protected nouveau(): void {
    const m = new Date();
    this.editer({ id: null, terminal: this.terminal || '', approId: null, jour: m,
      heure: `${String(m.getHours()).padStart(2, '0')}:${String(m.getMinutes()).padStart(2, '0')}`, gabiste: '',
      commentaire: '', journal: {}, inventaire: {}, transactionsAttente: 0, soldeComptable: null });
  }

  protected modifier(a: Arrete): void {
    this.editer({ id: a.id, terminal: a.terminal, approId: a.approId, jour: new Date(a.dateArrete),
      heure: a.dateArrete.substring(11, 16), gabiste: a.gabiste, commentaire: a.commentaire ?? '',
      journal: Object.fromEntries(a.journal.map((l) => [l.type, { nombre: l.cassette, rejet: l.rejet }])),
      inventaire: Object.fromEntries(a.inventaire.map((l) => [l.cassette, { nombre: l.nombre, rejet: l.rejet }])),
      transactionsAttente: a.transactionsAttente, soldeComptable: a.soldeComptable }, false);
  }

  private editer(f: Formulaire, reprendre = true): void {
    this.selection.set(null);
    this.message.set(null);
    this.erreur.set(null);
    this.formulaire.set(f);
    this.preparer(reprendre);
  }

  /** Appros à arrêter, cassettes et compteurs du journal au passage du gabiste. */
  protected preparer(reprendre = true): void {
    const f = this.formulaire();
    if (!f?.terminal || !f.jour) {
      this.preparation.set(null);
      return;
    }
    const jeton = ++this.jeton;
    this.service.preparationArrete(f.terminal, dateHeureIso(f.jour, f.heure)).subscribe({
      next: (p) => {
        if (jeton !== this.jeton) return; // réponse d'une demande plus ancienne (GAB ou date changés depuis)
        if (f.id && !p.approsNonArretes.some((a) => a.id === f.approId)) {
          // En modification, l'appro de l'arrêté n'est plus « à arrêter » : on le garde dans la liste.
          this.service.appro(f.approId!).subscribe((a) => this.preparation.set({ ...p, approsNonArretes: [a, ...p.approsNonArretes] }));
        } else {
          this.preparation.set(p);
          if (!f.approId) f.approId = p.approsNonArretes[0]?.id ?? null;
        }
        for (const c of p.cassettes) f.inventaire[c.cassette] ??= { nombre: 0, rejet: 0 };
        for (const t of new Set(p.cassettes.map((c) => c.type))) f.journal[t] ??= { nombre: 0, rejet: 0 };
        if (reprendre && p.passage) this.reprendreJournal();
        this.saisie.update((n) => n + 1);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  /** Recopie les compteurs du journal (billets en cassette et rejets avant remise à zéro). */
  protected reprendreJournal(): void {
    const f = this.formulaire();
    const p = this.preparation()?.passage;
    if (!f || !p?.cassettes) return;
    for (const t of this.types()) f.journal[t] = { nombre: p.cassettes[t - 1], rejet: p.rejets?.[t - 1] ?? 0 };
    f.heure = p.dateHeure.substring(11, 16);
    this.saisie.update((n) => n + 1);
  }

  protected cassettesDuType(t: number): string {
    return (this.preparation()?.cassettes ?? []).filter((c) => c.type === t).map((c) => c.cassette).join('+');
  }

  protected modifie(): void {
    this.saisie.update((n) => n + 1);
  }

  protected enregistrer(): void {
    const f = this.formulaire();
    if (!f) return;
    this.erreur.set(null);
    const s = { terminal: f.terminal, approId: f.approId, dateArrete: dateHeureIso(f.jour, f.heure), gabiste: f.gabiste,
      commentaire: f.commentaire, journal: f.journal, inventaire: f.inventaire,
      transactionsAttente: f.transactionsAttente || 0,
      soldeComptable: f.soldeComptable === null || (f.soldeComptable as unknown) === '' ? null : f.soldeComptable };
    (f.id ? this.service.modifierArrete(f.id, s) : this.service.creerArrete(s)).subscribe({
      next: (a) => {
        this.formulaire.set(null);
        this.selection.set(a);
        this.message.set(`Arrêté ${a.reference} enregistré : il doit maintenant être validé par une autre personne.`);
        this.rechercher(this.page());
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected peutDecider(a: Arrete): boolean {
    const login = this.moi()?.login;
    return this.peutValider && a.statut === 'SAISI' && login !== a.saisiPar && login !== a.modifiePar;
  }

  protected decider(a: Arrete, decision: 'validation' | 'annulation'): void {
    let motif: string | undefined;
    if (decision === 'annulation') {
      motif = prompt(`Motif de l'annulation de l'arrêté ${a.reference} :`) ?? undefined;
      if (!motif) return;
    }
    this.erreur.set(null);
    this.service.decider('arretes', a.id, decision, motif).subscribe({
      next: () => {
        this.message.set(`Arrêté ${a.reference} ${decision === 'validation' ? 'validé' : 'annulé'}.`);
        this.ouvrir(a.id);
        this.rechercher(this.page());
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private critere() {
    return { du: dateIso(this.du), au: dateIso(this.au), terminal: this.terminal, statut: this.statut };
  }
}
