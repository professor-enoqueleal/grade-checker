/**
 * Initial language (openspec capability serverless-i18n): the choice saved in the selector, then the browser languages,
 * then Portuguese. Only the choice of the selector is saved; the detected language is not.
 */
export const LOCALES = ['pt-BR', 'en'] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = 'pt-BR';
export const LOCALE_KEY = 'locale';

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && (LOCALES as readonly string[]).includes(value);
}

/** First browser language that is Portuguese (pt, pt-BR, pt-PT...) or English (en, en-US...), in the browser's order. */
export function fromBrowser(languages: readonly string[]): Locale | null {
  for (const language of languages) {
    const tag = language.toLowerCase();
    if (tag === 'pt' || tag.startsWith('pt-')) return 'pt-BR';
    if (tag === 'en' || tag.startsWith('en-')) return 'en';
  }
  return null;
}

export function savedLocale(): Locale | null {
  try {
    const value = localStorage.getItem(LOCALE_KEY);
    return isLocale(value) ? value : null;
  } catch {
    return null;   // storage blocked
  }
}

export function saveLocale(locale: Locale) {
  try { localStorage.setItem(LOCALE_KEY, locale); } catch { /* storage blocked */ }
}

function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') return [];
  if (navigator.languages?.length) return navigator.languages;
  return navigator.language ? [navigator.language] : [];
}

export function initialLocale(): Locale {
  return savedLocale() ?? fromBrowser(browserLanguages()) ?? DEFAULT_LOCALE;
}
