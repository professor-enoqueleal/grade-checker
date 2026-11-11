<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import type { TooltipItem } from 'chart.js';
import { api, type PeriodQuery } from '@/api';
import { ApiError, errorMessage } from '@/api/client';
import type { ContributionsReport, GroupDetail } from '@/api/types';
import { useConfig } from '@/composables/useConfig';
import { useSession } from '@/composables/useSession';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import ContributionsTable from '@/components/ContributionsTable.vue';
import ContributorCard from '@/components/ContributorCard.vue';
import GradingExplanation from '@/components/GradingExplanation.vue';
import HorizontalBarChart from '@/components/HorizontalBarChart.vue';
import LinkIdentityModal from '@/components/LinkIdentityModal.vue';
import PeriodSelector from '@/components/PeriodSelector.vue';
import { barDataset, CHART_COLORS } from '@/utils/charts';
import { formatMax, formatPercentage } from '@/utils/format';
import { periodText } from '@/i18n/periods';
import { gradeColumns, gradingNotice, MILESTONE_KEYS, milestoneHeader } from '@/utils/grades';

/**
 * Group detail (capabilities serverless-contributions-dashboard, milestone-period-filter, group-members, identity-aliases,
 * milestone-grading): group information, contributors and members, the dashboard per period (table + bar charts)
 * and how grades are calculated.
 */
const route = useRoute();
const { t } = useI18n();
const { session } = useSession();
const { config, load: loadConfig } = useConfig();
const isAdmin = computed(() => session.me?.isAdmin === true);

const groupId = Number(route.query.id);
const detail = shallowRef<GroupDetail | null>(null);
// messages are computed on render, so they follow a change of language
const pageError = ref<(() => string) | null>(null);
const membersError = ref<(() => string) | null>(null);
const memberBusy = ref(false);

// ------------------------------------------------------------ group, contributors and members

async function loadGroup() {
  detail.value = await api.group(groupId);
}

async function markMember(login: string) {
  memberBusy.value = true;
  membersError.value = null;
  try {
    await api.addMember(groupId, login, null, false);
    await Promise.all([loadGroup(), loadReport()]);
  } catch (e) {
    showMembersError(() => errorMessage(e, t('detail.addMemberError')));
  } finally {
    memberBusy.value = false;
  }
}

async function unmarkMember(login: string) {
  if (!window.confirm(t('members.confirmRemove', { login }))) return;
  memberBusy.value = true;
  membersError.value = null;
  try {
    await api.removeMember(groupId, login);
    await Promise.all([loadGroup(), loadReport()]);
  } catch (e) {
    showMembersError(() => errorMessage(e, t('detail.removeMemberError')));
  } finally {
    memberBusy.value = false;
  }
}

function showMembersError(text: () => string) {
  membersError.value = text;
  requestAnimationFrame(() => document.getElementById('members-alert')?.scrollIntoView({ block: 'center' }));
}

// ------------------------------------------------------------ dashboard

const period = ref<PeriodQuery>({ period: 'all' });
const report = shallowRef<ContributionsReport | null>(null);
const loading = ref(false);
const dashboardError = ref<(() => string) | null>(null);

async function loadReport() {
  loading.value = true;
  dashboardError.value = null;
  try {
    report.value = await api.contributions(groupId, period.value);
  } catch (e) {
    report.value = null;   // never show data of a previous load next to the error
    dashboardError.value = () => errorMessage(e, t('detail.dashboardError'));
  } finally {
    loading.value = false;
  }
}

function selectPeriod(key: string) {
  period.value = { period: key };
  loadReport();
}

function applyCustom(since: string, until: string) {
  period.value = { period: 'custom', since, until };
  loadReport();
}

const rows = computed(() => report.value?.rows ?? []);
const columns = computed(() => (report.value ? gradeColumns(report.value) : []));
const notice = computed(() => (report.value ? gradingNotice(report.value, isAdmin.value) : null));
const memberLogins = computed(() => detail.value?.members.map((m) => m.login) ?? []);
const totalLines = computed(() => rows.value.reduce((sum, r) => sum + r.totalAdditions + r.totalDeletions, 0));

/** one alert slot, as before: the truncation warning wins over "no contributions" */
const dashboardAlert = computed<{ type: string; text: string } | null>(() => {
  if (dashboardError.value) return { type: 'danger', text: dashboardError.value() };
  const r = report.value;
  if (!r) return null;
  if (r.truncated) return { type: 'warning', text: t('detail.truncated', { count: r.totalCommits }) };
  if (rows.value.length === 0 || totalLines.value === 0) return { type: 'info', text: t('detail.noContributions') };
  return null;
});

// grades chart: All -> M1 + M2 + M3 stacked (0 to 3 x maxPoints); milestone -> its grade; students only
const gradesChart = computed(() => {
  const r = report.value;
  const g = r?.grading;
  if (!r || !g || !(r.period === 'all' || r.period.startsWith('m'))) return null;
  const max = g.maxPoints ?? 3;
  if (r.period === 'all') {
    const graded = r.rows.filter((s) => s.total !== null && s.total !== undefined);
    if (graded.length === 0) return null;
    const provisional = g.provisionalMilestones ?? [];
    return {
      title: t('detail.gradesChartAll', { max: formatMax(max * MILESTONE_KEYS.length) }),
      labels: graded.map((s) => s.author),
      max: max * MILESTONE_KEYS.length,
      datasets: MILESTONE_KEYS.map((key, i) => barDataset(
        milestoneHeader(i, provisional.includes(key)),
        graded.map((s) => (s.milestoneGrades ? s.milestoneGrades[key] : null)),
        CHART_COLORS[key], MILESTONE_KEYS.length)),
    };
  }
  const graded = r.rows.filter((s) => s.grade !== null && s.grade !== undefined);
  if (graded.length === 0) return null;
  return {
    title: t('detail.gradesChartMilestone', { n: r.period.substring(1), max: formatMax(max) }) + (g.provisional ? t('common.inProgress') : ''),
    labels: graded.map((s) => s.author),
    max,
    datasets: [barDataset(t('detail.grade'), graded.map((s) => s.grade ?? null), CHART_COLORS[r.period] ?? CHART_COLORS.m2, 1)],
  };
});

// lines changed: additions + deletions stacked, every author, every period
const linesChart = computed(() => {
  if (rows.value.length === 0 || totalLines.value === 0) return null;
  const stats = rows.value;
  return {
    labels: stats.map((s) => s.author + (s.linked === false ? t('detail.noAccount') : '')),
    datasets: [
      barDataset(t('detail.additions'), stats.map((s) => s.totalAdditions), CHART_COLORS.additions, 2),
      barDataset(t('detail.deletions'), stats.map((s) => s.totalDeletions), CHART_COLORS.deletions, 2),
    ],
    footer: (items: TooltipItem<'bar'>[]) => {
      const s = stats[items[0].dataIndex];
      return t('detail.linesFooter', { lines: s.totalAdditions + s.totalDeletions, percentage: formatPercentage(s.percentage) });
    },
  };
});

const pointsLabel = (item: TooltipItem<'bar'>) => `${item.dataset.label}: ${Number(item.raw).toFixed(2)}`;
const linesLabel = (item: TooltipItem<'bar'>) => `${item.dataset.label}: ${item.raw}`;

// ------------------------------------------------------------ manual association of identities (administrators)

const linkModal = ref<InstanceType<typeof LinkIdentityModal> | null>(null);

async function unlink(identityKey: string, label: string) {
  if (!window.confirm(t('detail.confirmUnlink', { label }))) return;
  try {
    await api.unlinkIdentity(groupId, identityKey);
    await loadReport();
  } catch (e) {
    dashboardError.value = () => errorMessage(e, t('detail.unlinkError'));
  }
}

// ------------------------------------------------------------ first load: group, config and the dashboard (All)

onMounted(async () => {
  if (!Number.isInteger(groupId) || groupId <= 0) {
    pageError.value = () => t('common.idRequired');
    return;
  }
  try {
    await Promise.all([loadGroup(), loadConfig()]);
  } catch (e) {
    pageError.value = e instanceof ApiError && e.status === 404 ? () => t('common.groupNotFound')
      : () => errorMessage(e, t('detail.loadError'));
    return;
  }
  loadReport();
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: t('common.home'), to: '/home' }, { label: t('common.group', { id: Number.isInteger(groupId) ? groupId : '' }) }]" />

    <div v-if="pageError" class="alert alert-danger" role="alert" id="group-page-error">{{ pageError() }}</div>
    <div v-else-if="!detail || !config" class="gc-loader" role="status" :aria-label="t('common.loading')"></div>

    <template v-else>
      <h4>{{ t('detail.information') }}</h4>

      <div class="details row">
        <div class="col-12 col-lg-6">
          <div class="mb-2 row">
            <label for="groupName" class="col-sm-3 col-form-label">{{ t('detail.groupName') }}</label>
            <div class="col-sm-9">
              <input type="text" readonly class="form-control-plaintext" id="groupName" :value="detail.group.name">
            </div>
          </div>
          <div class="mb-2 row">
            <span class="col-sm-3 col-form-label">{{ t('detail.url') }}</span>
            <div class="col-sm-9">
              <a :href="detail.group.repositoryUrl" class="form-control-plaintext text-break" target="_blank" rel="noopener noreferrer">{{ detail.group.repositoryUrl }}</a>
            </div>
          </div>
          <div class="mb-2 row">
            <label for="owner" class="col-sm-3 col-form-label">{{ t('detail.owner') }}</label>
            <div class="col-sm-9">
              <input type="text" readonly class="form-control-plaintext" id="owner" :value="detail.group.owner">
            </div>
          </div>
          <div class="mb-2 row">
            <label for="repository" class="col-sm-3 col-form-label">{{ t('detail.repository') }}</label>
            <div class="col-sm-9">
              <input type="text" readonly class="form-control-plaintext" id="repository" :value="detail.group.repository">
            </div>
          </div>
        </div>

        <div class="col-12 col-lg-6">
          <div class="mb-2 row">
            <label for="number-of-contributors" class="col-sm-4 col-form-label">{{ t('detail.contributors') }}</label>
            <div class="col-sm-8">
              <input type="text" readonly class="form-control-plaintext" id="number-of-contributors"
                     :value="detail.contributorsError ? '—' : detail.contributorsCount ?? '—'">
            </div>
          </div>
          <div class="mb-2 row">
            <label for="members" class="col-sm-4 col-form-label">{{ t('detail.membersCount') }}</label>
            <div class="col-sm-8">
              <input type="text" readonly class="form-control-plaintext" id="members" :value="detail.members.length">
            </div>
          </div>
          <div v-if="isAdmin" class="row">
            <RouterLink :to="{ path: '/group/members', query: { id: String(groupId) } }" class="col-form-label" id="manage-members-link">{{ t('detail.manageMembers') }}</RouterLink>
          </div>
        </div>
      </div>

      <hr>

      <!-- contributors (GitHub) and members without commits (openspec capability group-members) -->
      <h4>{{ t('detail.contributorsAndMembers') }}</h4>

      <div v-if="detail.contributorsError" class="alert alert-warning" role="alert" id="contributors-error">{{ detail.contributorsError }}</div>
      <div v-if="membersError" id="members-alert" class="alert alert-danger small" role="alert">{{ membersError() }}</div>
      <p v-if="detail.cards.length === 0" class="text-body-secondary" id="no-cards">{{ t('detail.noCards') }}</p>

      <!-- 1 column < 576px, 2 >= 576px, 3 >= 768px, 4 >= 992px: narrower cards keep the square avatar small -->
      <div class="row row-cols-1 row-cols-sm-2 row-cols-md-3 row-cols-lg-4 g-4 mb-4">
        <div v-for="card in detail.cards" :key="card.login" class="col">
          <ContributorCard :card="card" :group-id="groupId" :is-admin="isAdmin" :busy="memberBusy"
                           @mark="markMember" @unmark="unmarkMember" />
        </div>
      </div>

      <hr>

      <!-- dashboard: loads with All when the page opens; the period buttons reload it -->
      <div id="content" class="text-center" :data-group-id="groupId" :data-admin="isAdmin ? 'true' : 'false'">
        <PeriodSelector :periods="config.periods" :selected="period.period === 'custom' ? null : period.period"
                        :busy="loading" :custom-until="config.customUntil" group-id="period-buttons"
                        @select="selectPeriod" @custom="applyCustom" />

        <template v-if="report">
          <h5 id="dashboard-title">{{ t('detail.dashboard') }}</h5>
          <p id="dashboard-summary" class="text-body-secondary small">{{ t('detail.summary', { commits: report.totalCommits, period: periodText(report) }) }}</p>
        </template>
        <div v-if="dashboardAlert" id="dashboard-alert" class="alert mb-4" :class="`alert-${dashboardAlert.type}`" role="alert">{{ dashboardAlert.text }}</div>
        <div v-if="report && notice" id="grading-alert" class="alert alert-info mb-4 small" role="status">{{ notice }}</div>
      </div>

      <div v-if="loading" class="gc-loader" id="loader" role="status" :aria-label="t('common.loading')"></div>

      <div v-if="report" class="row" id="contributions-section">
        <div class="col-12">
          <ContributionsTable :rows="rows" :columns="columns" :members="memberLogins" :is-admin="isAdmin"
                              @link="(key, label) => linkModal?.open(key, label)" @unlink="unlink" />
        </div>
      </div>

      <!-- bar charts: grades (All and milestones, students only) above, lines changed (every author) below -->
      <div class="row">
        <div v-if="gradesChart" class="col-12 mb-4" id="grades-chart-section">
          <h6 id="grades-chart-title">{{ gradesChart.title }}</h6>
          <HorizontalBarChart canvas-id="grades-chart" :labels="gradesChart.labels" :datasets="gradesChart.datasets"
                              :max="gradesChart.max" :chart-label="gradesChart.title" :tooltip-label="pointsLabel" />
        </div>
        <div v-if="linesChart" class="col-12 mb-4" id="lines-chart-section">
          <h6 id="lines-chart-title">{{ t('detail.linesChart') }}</h6>
          <HorizontalBarChart canvas-id="lines-chart" :labels="linesChart.labels" :datasets="linesChart.datasets"
                              :chart-label="t('detail.linesChart')" :tooltip-label="linesLabel"
                              :tooltip-footer="linesChart.footer" />
        </div>
      </div>

      <GradingExplanation :gx="config.gradingExplanation" />
    </template>
  </main>

  <!-- outside <main>, as in the JSP: the grading explanation is the last block of the page -->
  <LinkIdentityModal v-if="isAdmin && detail" ref="linkModal" :group-id="groupId" :students="detail.aliasTargets" @linked="loadReport" />
</template>
