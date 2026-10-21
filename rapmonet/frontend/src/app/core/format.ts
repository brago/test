/** Utilitaires d'affichage partagés. */

/** Date locale au format AAAA-MM-JJ (paramètre d'API). */
export function dateIso(d: Date | null | undefined): string | undefined {
  if (!d) return undefined;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Première valeur présente parmi plusieurs clés d'attributs (les codes varient selon le TC). */
export function premier(attributs: Record<string, unknown>, cles: string[]): unknown {
  for (const c of cles) {
    const v = attributs[c];
    if (v !== undefined && v !== null && v !== '') return v;
  }
  return null;
}
