import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { BinCarte, Cassette, ControleBin, EtatTableGim, Gab, GabService, PlageGim, ProfilGab, PropositionBin,
  RapportImportGim } from './gab.service';

type Edition =
  | { objet: 'gab'; nouveau: boolean; valeur: Gab }
  | { objet: 'profil'; nouveau: boolean; valeur: ProfilGab }
  | { objet: 'bin'; nouveau: boolean; valeur: BinCarte };

const LETTRES = ['A', 'B', 'C', 'D'];

/** Paramétrage GAB (déclaration des GAB, profils de cassettes, BIN cartes). */
@Component({
  selector: 'app-parametrage-gab',
  imports: [DatePipe, DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, MatSlideToggleModule, MatTableModule, MatTabsModule, MatTooltipModule],
  templateUrl: './parametrage-gab.html',
  styleUrl: './gab.scss',
})
export class ParametrageGab {
  private readonly service = inject(GabService);
  protected readonly modifiable = inject(AuthService).peut('PARAMETRAGE_GAB');

  protected readonly gabs = signal<Gab[]>([]);
  protected readonly profils = signal<ProfilGab[]>([]);
  protected readonly bins = signal<BinCarte[]>([]);
  protected readonly edition = signal<Edition | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly lettres = LETTRES;
  protected readonly marques = ['NCR', 'WINCOR', 'HYOSUNG', 'GRG', 'DIEBOLD'];
  protected readonly colonnesGab = ['terminalId', 'libelle', 'agence', 'marque', 'profil', 'compteCaisse', 'actif', 'actions'];
  protected readonly colonnesProfil = ['code', 'libelle', 'A', 'B', 'C', 'D', 'actions'];
  protected readonly colonnesBin = ['bin', 'libelle', 'typeCarte', 'gim', 'actions'];
  protected readonly colonnesPlage = ['prefixe', 'debut', 'fin', 'pays', 'actions'];

  // Table des BIN GIM
  protected readonly tableGim = signal<EtatTableGim | null>(null);
  protected readonly rapportGim = signal<RapportImportGim | null>(null);
  protected readonly controles = signal<Record<string, ControleBin>>({});
  protected readonly propositions = signal<PropositionBin[]>([]);
  protected readonly plages = signal<PlageGim[] | null>(null);
  protected rechercheGim = '';
  protected paysGim = '';

  constructor() {
    this.recharger();
  }

  protected libelleProfil(code: string): string {
    return this.profils().find((p) => p.code === code)?.libelle ?? code;
  }

  protected cassette(p: ProfilGab, lettre: string): Cassette | undefined {
    return p.cassettes.find((c) => c.cassette === lettre);
  }

  protected nouveauGab(): void {
    this.ouvrir({ objet: 'gab', nouveau: true, valeur: { terminalId: '', libelle: '', localisation: '', agence: '', marque: 'NCR',
      profilCode: this.profils()[0]?.code ?? '', compteCaisse: '', compteManquant: '', compteExcedent: '', actif: true } });
  }

  protected nouveauProfil(): void {
    this.ouvrir({ objet: 'profil', nouveau: true, valeur: { code: '', libelle: '', cassettes: [
      { cassette: 'A', coupure: 10000, type: 1 }, { cassette: 'B', coupure: 10000, type: 1 },
      { cassette: 'C', coupure: 5000, type: 2 }, { cassette: 'D', coupure: 2000, type: 3 }] } });
  }

  protected nouveauBin(bin = '', libelle = ''): void {
    this.ouvrir({ objet: 'bin', nouveau: true, valeur: { bin, libelle, typeCarte: 'DEBIT' } });
  }

  /** BIN à proposer pour une plage GIM : les chiffres communs au début et à la fin de la plage (8 au plus). */
  protected binDePlage(p: PlageGim): string {
    let i = 0;
    while (i < p.debut.length && p.debut[i] === p.fin[i]) i++;
    return p.debut.substring(0, Math.max(6, Math.min(i, 8)));
  }

  protected importerTableGim(e: Event): void {
    const input = e.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (!f) return;
    this.erreur.set(null);
    this.message.set(null);
    this.service.importerTableGim(f).subscribe({
      next: (r) => { this.rapportGim.set(r); this.recharger(); },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  /** Préfixe partagé : afficher ses plages pour choisir la sous-plage ; sinon déclarer le BIN. */
  protected traiterProposition(p: PropositionBin): void {
    if (p.prefixePartage) {
      this.rechercheGim = p.bin;
      this.paysGim = '';
      this.chercherPlages();
    } else {
      this.nouveauBin(p.bin);
    }
  }

  protected chercherPlages(): void {
    this.service.plagesGim(this.rechercheGim.trim(), this.paysGim).subscribe({
      next: (p) => this.plages.set(p),
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected modifier(e: Edition): void {
    const copie = structuredClone(e.valeur);
    if (e.objet === 'profil') {
      const p = copie as ProfilGab;
      // Une ligne par lettre ; coupure 0 = cassette absente.
      p.cassettes = LETTRES.map((l) => p.cassettes.find((c) => c.cassette === l) ?? { cassette: l, coupure: 0, type: 1 });
    }
    this.ouvrir({ ...e, valeur: copie } as Edition);
  }

  protected enregistrer(): void {
    const e = this.edition();
    if (!e) return;
    this.erreur.set(null);
    const requete: Observable<unknown> = e.objet === 'gab' ? this.service.enregistrerGab(e.valeur)
      : e.objet === 'profil' ? this.service.enregistrerProfil({ ...e.valeur, cassettes: e.valeur.cassettes.filter((c) => c.coupure > 0) })
        : this.service.enregistrerBin(e.valeur);
    requete.subscribe({ next: () => this.terminer('Enregistré.'), error: (x: unknown) => this.erreur.set(messageErreur(x)) });
  }

  protected supprimer(objet: Edition['objet'], cle: string): void {
    if (!confirm(`Supprimer ${cle} ?`)) return;
    const requete: Observable<unknown> = objet === 'gab' ? this.service.supprimerGab(cle)
      : objet === 'profil' ? this.service.supprimerProfil(cle) : this.service.supprimerBin(cle);
    requete.subscribe({ next: () => this.terminer(`${cle} supprimé.`), error: (x) => { this.message.set(null); this.erreur.set(messageErreur(x)); } });
  }

  protected fermer(): void {
    this.edition.set(null);
    this.erreur.set(null);
  }

  private ouvrir(e: Edition): void {
    this.message.set(null);
    this.erreur.set(null);
    this.edition.set(e);
  }

  private terminer(m: string): void {
    this.edition.set(null);
    this.message.set(m);
    this.recharger();
  }

  private recharger(): void {
    this.service.gabs().subscribe((g) => this.gabs.set(g));
    this.service.profils().subscribe((p) => this.profils.set(p));
    this.service.bins().subscribe((b) => this.bins.set(b));
    this.service.etatTableGim().subscribe((t) => this.tableGim.set(t));
    this.service.controlesBins().subscribe((c) => this.controles.set(Object.fromEntries(c.map((x) => [x.bin, x]))));
    this.service.propositionsBins().subscribe((p) => this.propositions.set(p));
  }
}
