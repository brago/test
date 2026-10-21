import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { messageErreur } from '../core/erreurs';
import { Commercant, CompenseService, ParametresCompense, RapportImport } from './compense.service';

/** Formulaire : taux en pourcentage (1,5) ; le serveur reçoit des fractions (0,015). */
interface Edition { nouveau: boolean; terminalId: string; nom: string; compte: string; gim: number; visa: number; mci: number; actif: boolean; }

/** Référentiel des commerçants de la compense TPE et comptes généraux. */
@Component({
  selector: 'app-commercants',
  imports: [DecimalPipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSlideToggleModule, MatTabsModule, MatTooltipModule],
  templateUrl: './commercants.html',
  styleUrl: './compense.scss',
})
export class Commercants {
  private readonly service = inject(CompenseService);

  protected readonly commercants = signal<Commercant[]>([]);
  protected readonly recherche = signal('');
  protected readonly edition = signal<Edition | null>(null);
  protected readonly parametres = signal<ParametresCompense | null>(null);
  protected readonly rapport = signal<RapportImport | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly comptes: { cle: keyof ParametresCompense; libelle: string; aide: string }[] = [
    { cle: 'compteEmisOnus', libelle: 'EMIS ONUS', aide: 'Débité pour les achats GIM des cartes de la banque' },
    { cle: 'compteAcqGim', libelle: 'ACQ GIM', aide: 'Débité pour les achats GIM des autres banques' },
    { cle: 'compteAcqVisa', libelle: 'ACQ VISA', aide: 'Débité pour les achats VISA' },
    { cle: 'compteAcqMci', libelle: 'ACQ MCI', aide: 'Débité pour les achats Mastercard' },
    { cle: 'compteCompensationVisaMci', libelle: 'CMPNSE VISAMCI', aide: 'Compensation VISA / Mastercard (récapitulatif)' },
    { cle: 'compteCompensationGim', libelle: 'CMPNSE GIM', aide: 'Compensation GIM (récapitulatif et commissions)' },
    { cle: 'compteCommission', libelle: 'CPTE COMM', aide: 'Commission acquéreur, part HT' },
    { cle: 'compteTaf', libelle: 'CPTE TAF', aide: 'Taxe sur les activités financières' },
    { cle: 'compteCommissionGim', libelle: 'COMM GIM', aide: 'Non utilisé par la macro (pour mémoire)' },
  ];

  protected readonly filtres = computed(() => {
    const r = this.recherche().trim().toLowerCase();
    return r ? this.commercants().filter((c) => (c.terminalId + ' ' + c.nom + ' ' + c.compte).toLowerCase().includes(r)) : this.commercants();
  });

  constructor() {
    this.recharger();
    this.service.parametres().subscribe((p) => this.parametres.set(p));
  }

  protected importer(e: Event): void {
    const input = e.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (!f) return;
    this.erreur.set(null);
    this.message.set(null);
    this.service.importer(f).subscribe({
      next: (r) => {
        this.rapport.set(r);
        this.recharger();
      },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected nouveau(): void {
    this.edition.set({ nouveau: true, terminalId: '', nom: '', compte: '', gim: 1.5, visa: 2.5, mci: 2.5, actif: true });
  }

  protected modifier(c: Commercant): void {
    this.edition.set({ nouveau: false, terminalId: c.terminalId, nom: c.nom, compte: c.compte, gim: +(c.tauxGim * 100).toFixed(3),
      visa: +(c.tauxVisa * 100).toFixed(3), mci: +(c.tauxMci * 100).toFixed(3), actif: c.actif });
  }

  protected enregistrer(): void {
    const e = this.edition();
    if (!e) return;
    this.erreur.set(null);
    this.service.enregistrer({ terminalId: e.terminalId, nom: e.nom, compte: e.compte, tauxGim: (e.gim || 0) / 100,
      tauxVisa: (e.visa || 0) / 100, tauxMci: (e.mci || 0) / 100, actif: e.actif }).subscribe({
      next: (c) => {
        this.edition.set(null);
        this.message.set(`Commerçant ${c.nom} (${c.terminalId}) enregistré.`);
        this.recharger();
      },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected supprimer(c: Commercant): void {
    if (!confirm(`Supprimer ${c.nom} (${c.terminalId}) ?`)) return;
    this.service.supprimer(c.terminalId).subscribe({ next: () => this.recharger(), error: (x) => this.erreur.set(messageErreur(x)) });
  }

  protected enregistrerParametres(): void {
    const p = this.parametres();
    if (!p) return;
    this.erreur.set(null);
    this.service.modifierParametres(p).subscribe({
      next: (n) => {
        this.parametres.set(n);
        this.message.set('Comptes de la compense enregistrés.');
      },
      error: (x) => this.erreur.set(messageErreur(x)),
    });
  }

  protected valeur(p: ParametresCompense, cle: keyof ParametresCompense): string {
    return String(p[cle] ?? '');
  }

  protected changer(p: ParametresCompense, cle: keyof ParametresCompense, v: string): void {
    (p as unknown as Record<string, string>)[cle] = v;
  }

  private recharger(): void {
    this.service.commercants().subscribe((c) => this.commercants.set(c));
  }
}
