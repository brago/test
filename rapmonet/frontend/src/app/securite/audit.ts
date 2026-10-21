import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { dateIso } from '../core/format';
import { Telechargement } from '../core/telechargement';
import { CritereAudit, EvenementAudit, SecuriteService } from './securite.service';

/** Piste d'audit : consultation filtrée et export Excel / PDF. */
@Component({
  selector: 'app-audit',
  imports: [
    DatePipe, FormsModule, MatButtonModule, MatDatepickerModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatPaginatorModule, MatProgressBarModule, MatSelectModule, MatTableModule,
  ],
  templateUrl: './audit.html',
  styleUrls: ['./securite.scss'],
})
export class Audit {
  private readonly service = inject(SecuriteService);
  private readonly telechargement = inject(Telechargement);

  protected du: Date | null = null;
  protected au: Date | null = null;
  protected login = '';
  protected action = '';
  protected readonly actions = signal<string[]>([]);
  protected readonly lignes = signal<EvenementAudit[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly taille = signal(50);
  protected readonly chargement = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly colonnes = ['horodatage', 'login', 'action', 'objet', 'detail', 'adresseIp', 'succes'];

  constructor() {
    this.service.actionsAudit().subscribe((a) => this.actions.set(a));
    this.rechercher();
  }

  private critere(): CritereAudit {
    return { du: dateIso(this.du), au: dateIso(this.au), login: this.login.trim(), action: this.action };
  }

  protected rechercher(page = 0): void {
    this.page.set(page);
    this.chargement.set(true);
    this.service.audit(this.critere(), page, this.taille()).subscribe({
      next: (r) => {
        this.lignes.set(r.elements);
        this.total.set(r.total);
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
  }

  protected changerPage(e: PageEvent): void {
    this.taille.set(e.pageSize);
    this.rechercher(e.pageIndex);
  }

  protected exporter(format: 'xlsx' | 'pdf'): void {
    this.erreur.set(null);
    this.telechargement.telecharger('/api/securite/audit/export', { format, ...this.critere() })
      .subscribe({ error: (e: Error) => this.erreur.set(e.message) });
  }
}
