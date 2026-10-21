import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { messageErreur } from '../core/erreurs';
import { Menu, Profil, SecuriteService } from './securite.service';

interface Saisie { creation: boolean; code: string; libelle: string; description: string; menus: Set<string>; systeme: boolean }

/**
 * Profils : un code, un libellé et les menus autorisés. Cocher un menu coche son parent ;
 * décocher un menu décoche ses enfants (un menu n'est autorisé que si son parent l'est).
 */
@Component({
  selector: 'app-profils',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule, MatListModule],
  templateUrl: './profils.html',
  styleUrls: ['./securite.scss', './profils.scss'],
})
export class Profils {
  private readonly service = inject(SecuriteService);

  protected readonly profils = signal<Profil[]>([]);
  protected readonly menus = signal<Menu[]>([]);
  protected readonly saisie = signal<Saisie | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  /** Profondeur de chaque menu dans l'arbre (0 = racine). */
  protected readonly profondeur = computed(() => {
    const parCode = new Map(this.menus().map((m) => [m.code, m]));
    const p = new Map<string, number>();
    for (const m of this.menus()) {
      let n = 0;
      for (let x = m.parentCode; x; x = parCode.get(x)?.parentCode ?? null) n++;
      p.set(m.code, n);
    }
    return p;
  });

  constructor() {
    this.service.menus().subscribe((m) => this.menus.set(m));
    this.recharger();
  }

  protected selectionner(p: Profil): void {
    this.message.set(null);
    this.erreur.set(null);
    this.saisie.set({ creation: false, code: p.code, libelle: p.libelle, description: p.description ?? '', menus: new Set(p.menus), systeme: p.systeme });
  }

  protected nouveau(): void {
    this.message.set(null);
    this.erreur.set(null);
    this.saisie.set({ creation: true, code: '', libelle: '', description: '', menus: new Set(['ACCUEIL']), systeme: false });
  }

  protected basculer(menu: Menu, coche: boolean): void {
    const s = this.saisie();
    if (!s) return;
    const menus = new Set(s.menus);
    const parCode = new Map(this.menus().map((m) => [m.code, m]));
    if (coche) {
      for (let x: string | null = menu.code; x; x = parCode.get(x)?.parentCode ?? null) menus.add(x);
    } else {
      const retirer = (code: string) => {
        menus.delete(code);
        this.menus().filter((m) => m.parentCode === code).forEach((m) => retirer(m.code));
      };
      retirer(menu.code);
    }
    this.saisie.set({ ...s, menus });
  }

  protected enregistrer(): void {
    const s = this.saisie();
    if (!s) return;
    const corps = { code: s.code.trim().toUpperCase(), libelle: s.libelle, description: s.description || null, menus: [...s.menus] };
    (s.creation ? this.service.creerProfil(corps) : this.service.modifierProfil(s.code, corps)).subscribe({
      next: (p) => {
        this.message.set(`Profil ${p.code} enregistré.`);
        this.recharger();
        this.selectionner(p);
        this.message.set(`Profil ${p.code} enregistré.`);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected supprimer(): void {
    const s = this.saisie();
    if (!s || !confirm(`Supprimer le profil ${s.code} ?`)) return;
    this.service.supprimerProfil(s.code).subscribe({
      next: () => {
        this.saisie.set(null);
        this.message.set(`Profil ${s.code} supprimé.`);
        this.recharger();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private recharger(): void {
    this.service.profils().subscribe((p) => this.profils.set(p));
  }
}
