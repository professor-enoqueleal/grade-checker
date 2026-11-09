<script setup lang="ts">
import { computed } from 'vue';
import { useTheme, type Theme } from '@/composables/useTheme';

/** Light / Dark / Auto dropdown in the bottom right corner of the sign-in page. */
const { theme, set } = useTheme();

const options: { value: Theme; label: string; icon: string }[] = [
  { value: 'light', label: 'Light', icon: 'bi-sun-fill' },
  { value: 'dark', label: 'Dark', icon: 'bi-moon-stars-fill' },
  { value: 'auto', label: 'Auto', icon: 'bi-circle-half' },
];
const activeIcon = computed(() => options.find((o) => o.value === theme.value)?.icon ?? 'bi-circle-half');
</script>

<template>
  <div class="dropdown position-fixed bottom-0 end-0 mb-3 me-3 bd-mode-toggle">
    <button class="btn btn-bd-primary py-2 dropdown-toggle d-flex align-items-center" id="bd-theme" type="button"
            aria-expanded="false" data-bs-toggle="dropdown" :aria-label="`Toggle theme (${theme})`">
      <i class="bi my-1" :class="activeIcon" aria-hidden="true"></i>
      <span class="visually-hidden" id="bd-theme-text">Toggle theme</span>
    </button>
    <ul class="dropdown-menu dropdown-menu-end shadow" aria-labelledby="bd-theme-text">
      <li v-for="option in options" :key="option.value">
        <button type="button" class="dropdown-item d-flex align-items-center" :class="{ active: theme === option.value }"
                :data-bs-theme-value="option.value" :aria-pressed="theme === option.value" @click="set(option.value)">
          <i class="bi me-2 opacity-50" :class="option.icon" aria-hidden="true"></i>
          {{ option.label }}
          <i class="bi bi-check2 ms-auto" :class="{ 'd-none': theme !== option.value }" aria-hidden="true"></i>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.btn-bd-primary {
  --bd-violet-bg: #712cf9;
  --bd-violet-rgb: 112.520718, 44.062154, 249.437846;
  --bs-btn-font-weight: 600;
  --bs-btn-color: var(--bs-white);
  --bs-btn-bg: var(--bd-violet-bg);
  --bs-btn-border-color: var(--bd-violet-bg);
  --bs-btn-hover-color: var(--bs-white);
  --bs-btn-hover-bg: #6528e0;
  --bs-btn-hover-border-color: #6528e0;
  --bs-btn-focus-shadow-rgb: var(--bd-violet-rgb);
  --bs-btn-active-color: var(--bs-btn-hover-color);
  --bs-btn-active-bg: #5a23c8;
  --bs-btn-active-border-color: #5a23c8;
}
.bd-mode-toggle { z-index: 1500; }
</style>
