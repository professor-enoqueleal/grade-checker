import { config } from '@vue/test-utils';
import { i18n } from '@/i18n';

/** The tests run in English (openspec capability serverless-i18n): the texts they check are the English ones. */
i18n.global.locale.value = 'en';
config.global.plugins = [i18n];
