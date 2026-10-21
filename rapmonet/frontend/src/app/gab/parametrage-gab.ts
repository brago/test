import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
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
import { BinCarte, Cassette, Gab, GabService, ProfilGab } from './gab.service';

type Edition =
  | { objet: 'gab'; nouveau: boolean; valeur: Gab }
  | { objet: 'profil'; nouveau: boolean; valeur: ProfilGab }
  | { objet: 'bin'; nouveau: boolean; valeur: BinCarte };

const LETTRES = ['A', 'B', 'C', 'D'];

/** Paramétrage GAB (déclaration des GAB, profils de cassettes, BIN cartes). */
@Component({
  selector: 'app-parametrage-gab',
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
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
  protected readonly colonnesBin = ['bin', 'libelle', 'typeCarte', 'actions'];

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

  protected nouveauBin(): void {
    this.ouvrir({ objet: 'bin', nouveau: true, valeur: { bin: '', libelle: '', typeCarte: 'DEBIT' } });
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
  }
}
