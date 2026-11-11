import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { i18n } from '@/i18n';
import { apiErrorText } from '@/i18n/apiErrors';
import { fromBrowser, initialLocale, LOCALE_KEY } from '@/i18n/locale';
import { ApiError, errorMessage } from '@/api/client';
import { useLocale } from '@/composables/useLocale';
import LanguageSelector from '@/components/LanguageSelector.vue';

function browser(languages: string[]) {
  vi.spyOn(navigator, 'languages', 'get').mockReturnValue(languages);
  vi.spyOn(navigator, 'language', 'get').mockReturnValue(languages[0] ?? '');
}

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
  i18n.global.locale.value = 'en';   // the other suites run in English
});

describe('initial language (openspec capability serverless-i18n)', () => {
  it('browser in Portuguese: pt-BR', () => {
    browser(['pt-BR', 'en']);
    expect(initialLocale()).toBe('pt-BR');
    browser(['pt-PT']);
    expect(initialLocale()).toBe('pt-BR');
  });

  it('browser in English: en', () => {
    browser(['en-US', 'pt-BR']);
    expect(initialLocale()).toBe('en');
  });

  it('other languages: the first supported one in the list, otherwise Portuguese', () => {
    browser(['es-ES', 'fr']);
    expect(initialLocale()).toBe('pt-BR');
    browser(['es-ES', 'en']);
    expect(initialLocale()).toBe('en');
    expect(fromBrowser(['english', 'ptx'])).toBeNull();   // only "pt", "pt-*", "en", "en-*"
  });

  it('browser without a language: Portuguese', () => {
    browser([]);
    expect(initialLocale()).toBe('pt-BR');
  });

  it('the saved choice wins over the browser; an invalid value is ignored', () => {
    browser(['en-US']);
    localStorage.setItem(LOCALE_KEY, 'pt-BR');
    expect(initialLocale()).toBe('pt-BR');
    localStorage.setItem(LOCALE_KEY, 'es');
    expect(initialLocale()).toBe('en');
  });

  it('blocked storage: the browser language', () => {
    browser(['en']);
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(initialLocale()).toBe('en');
  });

  it('the detected language is not saved', () => {
    browser(['en-US']);
    initialLocale();
    expect(localStorage.getItem(LOCALE_KEY)).toBeNull();
  });
});

describe('language selector', () => {
  it('two halves, Brazil (Português) and United States (English), the current one pressed', () => {
    i18n.global.locale.value = 'pt-BR';
    const w = mount(LanguageSelector);
    const halves = w.findAll('button');
    expect(halves.map((b) => b.attributes('aria-label'))).toEqual(['Português', 'English']);
    expect(halves.map((b) => b.attributes('title'))).toEqual(['Português', 'English']);
    expect(halves.map((b) => b.attributes('aria-pressed'))).toEqual(['true', 'false']);
    expect(halves.map((b) => b.find('svg').exists())).toEqual([true, true]);
    expect(w.find('#language-selector').attributes('aria-label')).toBe('Idioma');
  });

  it('a change applies at once, is saved and sets <html lang>', async () => {
    i18n.global.locale.value = 'pt-BR';
    const w = mount(LanguageSelector);
    await w.find('#language-en').trigger('click');
    expect(i18n.global.locale.value).toBe('en');
    expect(localStorage.getItem(LOCALE_KEY)).toBe('en');
    expect(document.documentElement.getAttribute('lang')).toBe('en');
    expect(w.find('#language-selector').attributes('aria-label')).toBe('Language');
    expect(w.find('#language-en').attributes('aria-pressed')).toBe('true');
    expect(w.find('#language-pt-BR').attributes('aria-pressed')).toBe('false');

    useLocale().set('pt-BR');
    expect(localStorage.getItem(LOCALE_KEY)).toBe('pt-BR');
    expect(document.documentElement.getAttribute('lang')).toBe('pt-BR');
  });
});

describe('API errors in the user language', () => {
  it('known code: translated', () => {
    i18n.global.locale.value = 'pt-BR';
    expect(errorMessage(new ApiError(409, 'has_aliases', 'Unlink the identities linked to this student before removing them.'), 'x'))
      .toBe('Desassocie as identidades ligadas a este aluno antes de removê-lo.');
    i18n.global.locale.value = 'en';
    expect(errorMessage(new ApiError(409, 'has_aliases', 'anything'), 'x'))
      .toBe('Unlink the identities linked to this student before removing them.');
  });

  it('generic code: translated by the message', () => {
    i18n.global.locale.value = 'pt-BR';
    expect(apiErrorText('not_found', 'Group not found.')).toBe('Grupo não encontrado.');
    expect(apiErrorText('error', 'Request failed (HTTP 503).')).toBe('A requisição falhou (HTTP 503).');
  });

  it('unknown error: the message of the backend; no message: the fallback', () => {
    i18n.global.locale.value = 'pt-BR';
    expect(errorMessage(new ApiError(400, 'invalid_period', 'Since must be before until.'), 'x')).toBe('Since must be before until.');
    expect(errorMessage(new Error('boom'), 'Tente de novo.')).toBe('Tente de novo.');
  });

  it('in English the texts are the messages of the backend', () => {
    expect(apiErrorText('forbidden', 'Only administrators can manage groups.')).toBe('Only administrators can manage groups.');
    expect(apiErrorText('network', 'Could not reach the server. Please try again.')).toBe('Could not reach the server. Please try again.');
  });
});
