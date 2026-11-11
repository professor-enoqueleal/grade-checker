<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api, type PeriodQuery } from '@/api';
import { errorMessage } from '@/api/client';
import type { CommitHistory } from '@/api/types';
import { useConfig } from '@/composables/useConfig';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import CommitActivityChart from '@/components/CommitActivityChart.vue';
import PeriodSelector from '@/components/PeriodSelector.vue';
import { shortSha } from '@/utils/format';
import { formatDay, formatTime } from '@/i18n/dates';
import { periodText } from '@/i18n/periods';

/**
 * Commit history of a student (openspec capability serverless-commit-history-page): commits attributed to the login in the
 * period (same history and attribution as the dashboard), frequency chart and table. The filter lives in the URL.
 */
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const { config, load: loadConfig } = useConfig();

const q = (name: string) => (typeof route.query[name] === 'string' ? (route.query[name] as string) : '');
const groupId = Number(q('groupId'));
const author = q('author');
const query: PeriodQuery = { period: q('period') || 'all', since: q('date-since') || undefined, until: q('date-until') || undefined };

const history = shallowRef<CommitHistory | null>(null);
// computed on render, so it follows a change of language
const error = ref<(() => string) | null>(null);
const loading = ref(true);

const periods = computed(() => history.value?.periods ?? config.value?.periods ?? []);
const selected = computed(() => {
  const key = history.value?.period ?? query.period;
  return key === 'custom' ? null : key;
});

function linkFor(key: string) {
  return { name: 'commits', query: { groupId: String(groupId), author, period: key } };
}

function applyCustom(since: string, until: string) {
  router.push({ name: 'commits', query: { groupId: String(groupId), author, period: 'custom', 'date-since': since, 'date-until': until } });
}

onMounted(async () => {
  if (!Number.isInteger(groupId) || groupId <= 0 || !author) {
    error.value = () => t('commits.paramsRequired');
    loading.value = false;
    return;
  }
  try {
    const [h] = await Promise.all([api.commits(groupId, author, query), loadConfig()]);
    history.value = h;
  } catch (e) {
    error.value = () => errorMessage(e, t('commits.loadError'));
    loadConfig().catch(() => undefined);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[
      { label: t('common.home'), to: '/home' },
      { label: t('common.group', { id: Number.isInteger(groupId) ? groupId : '' }), to: { path: '/detail', query: { id: String(groupId) } } },
      { label: t('commits.crumb') },
    ]" />

    <h4>{{ t('commits.title') }}</h4>

    <div class="details">
      <div class="mb-3 row">
        <span class="col-sm-2 col-form-label">{{ t('commits.contributor') }}</span>
        <div class="col-sm-10">
          <span class="form-control-plaintext" id="author">{{ author }}</span>
        </div>
      </div>

      <!-- identities without account counted for this student -->
      <div v-if="history && history.alsoCounted.length > 0" class="mb-3 row">
        <span class="col-sm-2 col-form-label">{{ t('commits.alsoCounted') }}</span>
        <div class="col-sm-10">
          <span class="form-control-plaintext text-break" id="also-counted">{{ history.alsoCounted.join(', ') }}</span>
        </div>
      </div>

      <div class="mb-3 row">
        <span class="col-sm-2 col-form-label">{{ t('commits.total') }}</span>
        <div class="col-sm-10">
          <span class="form-control-plaintext" id="total-of-commits">{{ history ? history.commits.length : '—' }}</span>
        </div>
      </div>

      <div class="mb-3 row">
        <span class="col-sm-2 col-form-label">{{ t('commits.period') }}</span>
        <div class="col-sm-10">
          <PeriodSelector v-if="periods.length" :periods="periods" :selected="selected" :link-for="linkFor"
                          :custom-since="history?.customSince ?? query.since" :custom-until="history?.customUntil ?? query.until ?? config?.customUntil"
                          :custom-open="query.period === 'custom'" @custom="applyCustom">
            <template #after-buttons>
              <div v-if="history" class="form-text" id="period-text">{{ t('commits.periodText', { period: periodText(history) }) }}</div>
            </template>
          </PeriodSelector>
        </div>
      </div>
    </div>

    <hr>

    <div v-if="history?.periodWarning" class="alert alert-warning" role="alert">{{ history.periodWarning }}</div>

    <div v-if="loading" class="gc-loader" role="status" :aria-label="t('common.loading')"></div>
    <div v-else-if="error" class="alert alert-danger" role="alert" id="commits-error">{{ error() }}</div>
    <h4 v-else-if="history && history.commits.length === 0">{{ t('commits.empty') }}</h4>

    <template v-else-if="history">
      <div v-if="history.truncated" class="alert alert-warning" role="alert">
        {{ t('commits.truncated') }}
      </div>

      <CommitActivityChart v-if="history.activity" :activity="history.activity" />

      <div class="table-responsive">
        <table class="table table-striped table-hover">
          <thead>
            <tr>
              <th scope="col">{{ t('commits.colDate') }}</th>
              <th scope="col">{{ t('commits.colMessage') }}</th>
              <th scope="col">{{ t('commits.colCommit') }}</th>
              <th scope="col" class="d-none d-sm-table-cell">{{ t('commits.colIdentity') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="commit in history.commits" :key="commit.sha">
              <td class="commit-date"><span class="text-nowrap">{{ commit.date ? formatDay(commit.date) : commit.day }}</span> <span class="text-nowrap">{{ commit.date ? formatTime(commit.date) : commit.time }}</span></td>
              <td class="commit-message">
                <div class="commit-message-text" :title="commit.message">{{ commit.message }}</div>
                <!-- phones: the Identity column is hidden and the identity (when any) goes below the message -->
                <div v-if="commit.identity" class="commit-identity-inline d-sm-none small text-body-secondary" :title="commit.identity">
                  {{ commit.identity }}
                  <span v-if="commit.manual" class="badge text-bg-secondary fw-normal ms-1">{{ t('commits.manual') }}</span>
                </div>
              </td>
              <td>
                <a v-if="commit.url" :href="commit.url" target="_blank" rel="noopener noreferrer" class="commit-sha" :title="commit.sha"><code>{{ shortSha(commit.sha) }}</code></a>
                <code v-else class="commit-sha" :title="commit.sha">{{ shortSha(commit.sha) }}</code>
              </td>
              <td class="commit-identity d-none d-sm-table-cell">
                <template v-if="!commit.identity">&mdash;</template>
                <template v-else>
                  {{ commit.identity }}
                  <span v-if="commit.manual" class="badge text-bg-secondary fw-normal ms-1 manual-badge">{{ t('commits.manual') }}</span>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </main>
</template>

<style scoped>
/* the message column takes the width left by the others */
.commit-message { max-width: 0; width: 100%; }
/* one line per message (and per identity on phones), "..." where the space ends */
.commit-message-text,
.commit-identity-inline { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* phones: date and time on two lines; long identities (e-mails) break anywhere */
@media (min-width: 576px) {
  .commit-date { white-space: nowrap; }
}
.commit-identity { overflow-wrap: anywhere; }
</style>
