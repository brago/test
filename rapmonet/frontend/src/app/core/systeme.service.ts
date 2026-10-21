import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface InfoSysteme {
  application: string;
  version: string;
  baseDeDonnees: { produit: string; version: string; schemaVersion: string };
}

@Injectable({ providedIn: 'root' })
export class SystemeService {
  private readonly http = inject(HttpClient);

  info(): Observable<InfoSysteme> {
    return this.http.get<InfoSysteme>('/api/systeme/info');
  }
}
