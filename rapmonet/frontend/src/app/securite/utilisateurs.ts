import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { messageErreur } from '../core/erreurs';
import { Profil, SecuriteService, Utilisateur } from './securite.service';

type Edition =
  | { mode: 'creation'; login: string; nom: string; agence: string; email: string; profilCode: string; motDePasse: string }
  | { mode: 'modification'; login: string; nom: string; agence: string; email: string; profilCode: string; actif: boolean }
  | { mode: 'reinitialisation'; login: string; motDePasse: string; doitChanger: boolean };

/** Gestion des comptes : création, modification, activation, déverrouillage, réinitialisation du mot de passe. */
@Component({
  selector: 'app-utilisateurs',
  imports: [
    DatePipe, FormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatChipsModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatTableModule, MatTooltipModule,
  ],
  templateUrl: './utilisateurs.html',
  styleUrl: './securite.scss',
})
export class Utilisateurs {
  private readonly service = inject(SecuriteService);
  protected readonly moi = inject(AuthService).utilisateur;

  protected readonly utilisateurs = signal<Utilisateur[]>([]);
  protected readonly profils = signal<Profil[]>([]);
  protected readonly edition = signal<Edition | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly colonnes = ['login', 'nom', 'agence', 'profil', 'statut', 'derniereConnexion', 'actions'];

  constructor() {
    this.recharger();
    this.service.profils().subscribe((p) => this.profils.set(p));
  }

  protected nouveau(): void {
    this.ouvrir({ mode: 'creation', login: '', nom: '', agence: '', email: '', profilCode: '', motDePasse: '' });
  }

  protected modifier(u: Utilisateur): void {
    this.ouvrir({ mode: 'modification', login: u.login, nom: u.nom, agence: u.agence ?? '', email: u.email ?? '', profilCode: u.profilCode, actif: u.actif });
  }

  protected reinitialiser(u: Utilisateur): void {
    this.ouvrir({ mode: 'reinitialisation', login: u.login, motDePasse: '', doitChanger: true });
  }

  protected reinitialiserDeuxFacteurs(u: Utilisateur): void {
    if (!confirm(`Réinitialiser la double authentification de ${u.login} ? Il devra la réactiver avec son nouveau téléphone.`)) return;
    this.service.reinitialiserDeuxFacteurs(u.login).subscribe({
      next: () => this.terminer(`Double authentification de ${u.login} réinitialisée.`),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected deverrouiller(u: Utilisateur): void {
    this.service.deverrouiller(u.login).subscribe({
      next: () => this.terminer(`Compte ${u.login} déverrouillé.`),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected enregistrer(): void {
    const e = this.edition();
    if (!e) return;
    this.erreur.set(null);
    const requete =
      e.mode === 'creation'
        ? this.service.creerUtilisateur({ login: e.login, nom: e.nom, agence: e.agence, email: e.email, profilCode: e.profilCode, motDePasseTemporaire: e.motDePasse })
        : e.mode === 'modification'
          ? this.service.modifierUtilisateur(e.login, { nom: e.nom, agence: e.agence || null, email: e.email || null, profilCode: e.profilCode, actif: e.actif })
          : this.service.reinitialiser(e.login, e.motDePasse, e.doitChanger);
    const libelle = e.mode === 'creation' ? 'créé' : e.mode === 'modification' ? 'modifié' : 'réinitialisé';
    requete.subscribe({
      next: () => this.terminer(`Compte ${e.login} ${libelle}.`),
      error: (x) => this.erreur.set(messageErreur(x)),
    });
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

  private terminer(message: string): void {
    this.edition.set(null);
    this.message.set(message);
    this.recharger();
  }

  private recharger(): void {
    this.service.utilisateurs().subscribe((u) => this.utilisateurs.set(u));
  }
}
