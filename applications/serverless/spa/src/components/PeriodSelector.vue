<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import type { RouteLocationRaw } from 'vue-router';
import type { PeriodOption } from '@/api/types';
import { endOfToday } from '@/utils/format';

/**
 * Period filter (openspec capability milestone-period-filter): Milestone 1/2/3 and All, plus a Custom period.
 * - dashboard: buttons that emit "select" (the page reloads the data);
 * - commit history: links (`linkFor`), so the URL represents the filter.
 * Milestones not started (or not configured) are disabled, with the reason in the title.
 */
const props = defineProps<{
  periods: PeriodOption[];
  selected: string | null;
  busy?: boolean;
  customSince?: string;
  customUntil?: string;
  customOpen?: boolean;
  linkFor?: (key: string) => RouteLocationRaw;
  /** id of the button group (#period-buttons in the dashboard) */
  groupId?: string;
}>();
const emit = defineEmits<{ select: [key: string]; custom: [since: string, until: string] }>();
const { t } = useI18n();

const since = ref(props.customSince ?? '');
const until = ref(props.customUntil ?? '');
const open = ref(!!props.customOpen);
// the values may arrive after the first render (commit history: with the API response)
watch(() => props.customSince, (value) => { since.value = value ?? ''; });
watch(() => props.customUntil, (value) => { until.value = value ?? ''; });
const max = endOfToday();

function select(key: string) {
  open.value = false;
  emit('select', key);
}
</script>

<template>
  <div class="btn-group flex-wrap period-filter" :class="linkFor ? '' : 'mb-2'" role="group" :aria-label="t('period.label')" :id="groupId">
    <template v-for="option in periods" :key="option.key">
      <template v-if="linkFor">
        <RouterLink v-if="option.enabled" :to="linkFor(option.key)" class="btn btn-sm"
                    :class="option.key === selected ? 'btn-primary active' : 'btn-outline-primary'"
                    :title="option.hint" :aria-current="option.key === selected ? 'true' : undefined">{{ option.label }}</RouterLink>
        <span v-else class="btn btn-sm btn-outline-secondary disabled" aria-disabled="true" :title="option.hint">{{ option.label }}</span>
      </template>
      <button v-else type="button" class="btn btn-sm period-button"
              :class="option.key === selected ? 'btn-primary active' : 'btn-outline-primary'"
              :data-period="option.key" :title="option.hint" :disabled="!option.enabled || busy"
              @click="select(option.key)">{{ option.label }}</button>
    </template>
  </div>
  <slot name="after-buttons"></slot>

  <details id="custom-period" :class="linkFor ? 'mt-3' : 'mb-3'" :open="open" @toggle="open = ($event.target as HTMLDetailsElement).open">
    <summary class="small">{{ t('period.custom') }}</summary>
    <form class="d-inline-flex flex-wrap gap-2 align-items-end mt-2" @submit.prevent="emit('custom', since, until)">
      <div class="text-start">
        <label for="since" class="form-label small mb-0">{{ t('period.since') }}</label>
        <input type="datetime-local" class="form-control form-control-sm" id="since" v-model="since" :max="max">
      </div>
      <div class="text-start">
        <label for="until" class="form-label small mb-0">{{ t('period.until') }}</label>
        <input type="datetime-local" class="form-control form-control-sm" id="until" v-model="until" :max="max">
      </div>
      <button type="submit" class="btn btn-sm btn-primary" id="applyCustomPeriod" :disabled="busy">{{ t('period.apply') }}</button>
    </form>
  </details>
</template>

<style scoped>
.period-filter .btn.disabled {
  pointer-events: auto; /* keep the tooltip (title) on disabled milestones */
  cursor: not-allowed;
}
</style>
