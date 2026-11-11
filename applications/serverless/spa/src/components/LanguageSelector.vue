<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { useLocale } from '@/composables/useLocale';
import { isLocale, LOCALES } from '@/i18n/locale';

/** "Português" / "English" (openspec capability serverless-i18n), in the navbar and on the sign-in page. */
const { t } = useI18n();
const { locale, set } = useLocale();

function change(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  if (isLocale(value)) set(value);
}
</script>

<template>
  <select class="form-select form-select-sm w-auto language-selector" id="language-selector"
          :aria-label="t('language.label')" :value="locale" @change="change">
    <option v-for="option in LOCALES" :key="option" :value="option" :lang="option">{{ t(`language.${option}`) }}</option>
  </select>
</template>
