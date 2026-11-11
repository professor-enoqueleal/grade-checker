import { i18n } from '@/i18n';

/**
 * Dates in the current language (openspec capability serverless-i18n). The API sends local values of the milestone zone
 * ("2026-10-21" or "2026-10-21T11:05"): they are formatted as they are, never converted to the browser's zone.
 * English reproduces the formats of the backend ("Oct 21, 2026", "Tue, Oct 21, 2026"); Portuguese uses dd/MM/yyyy.
 */
const english = () => i18n.global.locale.value === 'en';

function utc(local: string): Date {
  const [y, m, d] = local.slice(0, 10).split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function format(local: string, en: Intl.DateTimeFormatOptions, pt: Intl.DateTimeFormatOptions): string {
  const isEnglish = english();
  return new Intl.DateTimeFormat(isEnglish ? 'en-US' : 'pt-BR', { timeZone: 'UTC', ...(isEnglish ? en : pt) }).format(utc(local));
}

/** "Oct 21, 2026" / "21/10/2026" */
export function formatDay(local: string): string {
  return format(local, { month: 'short', day: 'numeric', year: 'numeric' }, { day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** "11:05" (24 hours in both languages) */
export function formatTime(local: string): string {
  return local.slice(11, 16);
}

/** "Oct 21, 2026 11:05" / "21/10/2026 11:05" */
export function formatDayTime(local: string): string {
  return `${formatDay(local)} ${formatTime(local)}`;
}

/** Axis of the frequency chart: "Oct 21" / "21/10" */
export function formatShortDay(local: string): string {
  return format(local, { month: 'short', day: 'numeric' }, { day: '2-digit', month: '2-digit' });
}

/** Tooltip of a day: "Wed, Oct 21, 2026" / "qua., 21/10/2026" */
export function formatWeekday(local: string): string {
  if (english()) return format(local, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }, {});
  return `${format(local, {}, { weekday: 'short' })}, ${formatDay(local)}`;
}
