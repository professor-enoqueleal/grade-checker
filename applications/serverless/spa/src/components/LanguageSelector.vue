<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { useLocale } from '@/composables/useLocale';
import { LOCALES } from '@/i18n/locale';

/**
 * Language selector (openspec capability serverless-i18n), in the navbar and on the sign-in page: one round button split
 * in two halves, the flag of Brazil (Português) and the flag of the United States (English). The current language is in color;
 * the other is faded until hovered or focused. The flags are simplified SVG drawn here (emoji flags are letters on Windows).
 */
const { t } = useI18n();
const { locale, set } = useLocale();

/** US stripes: the 6 white ones of the 13 */
const STRIPE = 20 / 13;
const whiteStripes = [1, 3, 5, 7, 9, 11].map((i) => i * STRIPE);
const stars = [1.6, 4.1, 6.6, 9.1].flatMap((x) => [1.6, 4.1, 6.6, 9.1].map((y) => ({ x, y })));
</script>

<template>
  <div class="language-selector" role="group" id="language-selector" :aria-label="t('language.label')">
    <button v-for="option in LOCALES" :key="option" type="button" class="language-flag" :class="{ active: option === locale }"
            :id="`language-${option}`" :lang="option" :aria-pressed="option === locale ? 'true' : 'false'"
            :aria-label="t(`language.${option}`)" :title="t(`language.${option}`)" @click="set(option)">
      <svg v-if="option === 'pt-BR'" viewBox="0 0 28 20" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
        <rect width="28" height="20" fill="#009c3b" />
        <polygon points="14,2.2 25.6,10 14,17.8 2.4,10" fill="#ffdf00" />
        <circle cx="14" cy="10" r="4.6" fill="#002776" />
        <path d="M9.5 9.1 Q14 7.4 18.5 10.6" stroke="#fff" stroke-width="0.9" fill="none" />
      </svg>
      <svg v-else viewBox="0 0 28 20" preserveAspectRatio="xMinYMid slice" aria-hidden="true" focusable="false">
        <rect width="28" height="20" fill="#b22234" />
        <rect v-for="y in whiteStripes" :key="y" y="0" :transform="`translate(0 ${y})`" width="28" :height="STRIPE" fill="#fff" />
        <rect width="11.2" :height="STRIPE * 7" fill="#3c3b6e" />
        <circle v-for="(s, i) in stars" :key="i" :cx="s.x + 0.6" :cy="s.y" r="0.55" fill="#fff" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.language-selector {
  display: inline-flex;
  border: 1px solid var(--bs-border-color);
  border-radius: 50%;
  overflow: hidden;
  vertical-align: middle;
}
.language-flag {
  display: block;
  width: 14px;
  height: 28px;
  padding: 0;
  border: 0;
  background: none;
  line-height: 0;
  cursor: pointer;
}
.language-flag + .language-flag {
  border-left: 1px solid var(--bs-border-color);
}
.language-flag svg {
  display: block;
  width: 100%;
  height: 100%;
}
.language-flag:not(.active) {
  filter: grayscale(1);
  opacity: 0.45;
}
.language-flag:not(.active):hover,
.language-flag:focus-visible {
  filter: none;
  opacity: 1;
}
.language-flag:focus-visible {
  outline: 2px solid var(--bs-focus-ring-color, #86b7fe);
  outline-offset: -2px;
}
</style>
