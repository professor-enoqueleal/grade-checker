<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';
import { useI18n } from 'vue-i18n';

/** Breadcrumb of every page: links to the previous levels, the last item is the current page. Followed by a rule. */
export interface Crumb {
  label: string;
  to?: RouteLocationRaw;
  id?: string;
}
defineProps<{ items: Crumb[] }>();
const { t } = useI18n();
</script>

<template>
  <br>
  <nav style="--bs-breadcrumb-divider: '>';" :aria-label="t('breadcrumb.label')">
    <ol class="breadcrumb">
      <template v-for="(item, i) in items" :key="i">
        <li v-if="i < items.length - 1 || items.length === 1 && item.to" class="breadcrumb-item" :class="{ active: items.length === 1 }">
          <RouterLink :to="item.to ?? '/home'" :id="item.id">{{ item.label }}</RouterLink>
        </li>
        <li v-else class="breadcrumb-item active" aria-current="page">{{ item.label }}</li>
      </template>
    </ol>
  </nav>
  <hr>
</template>
