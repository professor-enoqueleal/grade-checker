/** Rounds half up to 2 places (as String.format("%.2f") on the backend), avoiding 1.005 -> "1.00". */
export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

/** "—" when there is no grade, otherwise 2 decimal places ("2.35"). */
export function formatGrade(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : round2(Number(value)).toFixed(2);
}

/** "12.50%" */
export function formatPercentage(value: number): string {
  return round2(Number(value)).toFixed(2) + '%';
}

/** Axis maximum: "9", "4.5". */
export function formatMax(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

/** Short text of a repository URL: "owner/repository" (the link keeps the full URL). */
export function repositoryLabel(url: string | null | undefined): string {
  if (!url) return '';
  const label = url.replace(/^https?:\/\/(www\.)?github\.com\//i, '');
  return label.endsWith('/') ? label.slice(0, -1) : label;
}

/** Only https://github.com/ links are rendered as links. */
export function gitHubLink(url: string | null | undefined): string | null {
  return url && url.startsWith('https://github.com/') ? url : null;
}

/** First 7 characters of the hash, as shown by GitHub. */
export function shortSha(sha: string): string {
  return sha.length <= 7 ? sha : sha.substring(0, 7);
}

/** End of today for the Custom period pickers (yyyy-MM-ddT23:59). */
export function endOfToday(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T23:59`;
}
