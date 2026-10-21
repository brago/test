import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { AdaptateurDateFr } from './adaptateur-date';

describe('AdaptateurDateFr', () => {
  let adaptateur: DateAdapter<Date>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNativeDateAdapter(), { provide: DateAdapter, useClass: AdaptateurDateFr },
        { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' }],
    });
    adaptateur = TestBed.inject(DateAdapter);
  });

  it('lit une date saisie au format JJ/MM/AAAA', () => {
    const d = adaptateur.parse('05/06/2026', null)!;
    expect([d.getFullYear(), d.getMonth() + 1, d.getDate()]).toEqual([2026, 6, 5]);
  });

  it('accepte une année sur deux chiffres et d\'autres séparateurs', () => {
    const d = adaptateur.parse('22.05.26', null)!;
    expect([d.getFullYear(), d.getMonth() + 1, d.getDate()]).toEqual([2026, 5, 22]);
  });

  it('refuse une date impossible', () => {
    expect(adaptateur.isValid(adaptateur.parse('31/02/2026', null)!)).toBe(false);
  });

  it('affiche la date au format français', () => {
    expect(adaptateur.format(new Date(2026, 4, 22), { year: 'numeric', month: '2-digit', day: '2-digit' })).toBe('22/05/2026');
  });
});
