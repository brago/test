/** Utilitaires d'affichage partagés. */

/** Date locale au format AAAA-MM-JJ (paramètre d'API). */
export function dateIso(d: Date | null | undefined): string | undefined {
  if (!d) return undefined;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

