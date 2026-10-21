import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ParametrageService, TcFormat } from './parametrage.service';

/** Consultation du paramétrage du fichier LIS : sous-fichiers, TC, TCR et attributs avec leurs positions. */
@Component({
  selector: 'app-format-lis',
  imports: [MatCardModule, MatExpansionModule, MatFormFieldModule, MatIconModule, MatInputModule, MatTableModule, MatTooltipModule],
  templateUrl: './format-lis.html',
  styleUrl: './format-lis.scss',
})
export class FormatLis {
  protected readonly format = toSignal(inject(ParametrageService).format('LIS_GIM'));
  protected readonly filtre = signal('');
  protected readonly colonnes = ['position', 'taille', 'code', 'libelle', 'type', 'options'];

  protected readonly tcs = computed<TcFormat[]>(() => {
    const f = this.format();
    if (!f) return [];
    const q = this.filtre().trim().toLowerCase();
    return Object.values(f.tcs)
      .filter((t) => !q || t.code.includes(q) || t.libelle.toLowerCase().includes(q)
        || t.tcrs.some((r) => r.attributs.some((a) => a.code.toLowerCase().includes(q) || a.libelle.toLowerCase().includes(q))))
      .sort((a, b) => a.code.localeCompare(b.code));
  });

  protected nbAttributs(t: TcFormat): number {
    return t.tcrs.reduce((n, r) => n + r.attributs.length, 0);
  }
}
