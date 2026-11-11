import { createI18n } from 'vue-i18n';
import ptBR from './pt-BR';
import en from './en';
import { DEFAULT_LOCALE, initialLocale } from './locale';

/**
 * vue-i18n in Composition mode (openspec capability serverless-i18n). `t` works outside components too (utils, router);
 * a computed or template that calls it follows the language.
 */
export type MessageSchema = typeof ptBR;

export const i18n = createI18n<[MessageSchema], 'pt-BR' | 'en', false>({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: { 'pt-BR': ptBR, en },
  missingWarn: false,
  fallbackWarn: false,
});

export const t = i18n.global.t;
export const te = i18n.global.te;
