import { computed } from 'vue';
import { i18n } from '@/i18n';
import { saveLocale, type Locale } from '@/i18n/locale';

/**
 * Current language and the change made in the selector (openspec capability serverless-i18n). The change is immediate
 * (no reload, the screen keeps its state); <html lang> follows it, and App.vue updates the tab title.
 */
const current = i18n.global.locale;

export function applyLocale(locale: Locale) {
  current.value = locale;
  document.documentElement.setAttribute('lang', locale);
}

export function useLocale() {
  function set(locale: Locale) {
    saveLocale(locale);
    applyLocale(locale);
  }
  return { locale: computed(() => current.value as Locale), set };
}
