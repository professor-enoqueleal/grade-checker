<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/client';
import type { GroupGrades } from '@/api/types';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import { download, gradesCsv, type CsvGroup } from '@/utils/csv';
import { formatGrade } from '@/utils/format';
import { MILESTONE_KEYS, milestoneHeader } from '@/utils/grades';

/**
 * Class grades (openspec capability class-gradebook), administrators only: every student of every group, grades of
 * the period All. Groups load progressively, at most 3 at a time (each one may take a few GitHub reports).
 * The CSV is generated here, from the grades already loaded (it was /grades.csv on the server).
 */
const PARALLEL = 3;
type GradeKey = 'm1' | 'm2' | 'm3' | 'total';
type SortKey = 'group' | 'login' | GradeKey;

interface GroupState {
  id: number;
  name: string;
  students: string[];
  status: 'pending' | 'done' | 'error';
  grades: GroupGrades | null;
  /** the failure of this group; the text is computed on render (it follows a change of language) */
  failure: unknown;
}

interface Row {
  key: string;
  group: GroupState;
  login: string;          // '' for a group without students
}

const { t } = useI18n();
// messages are computed on render, so they follow a change of language
const forbidden = ref<(() => string) | null>(null);
const loadError = ref<(() => string) | null>(null);
const groups = ref<GroupState[] | null>(null);
const filter = ref('');
const sort = reactive<{ key: SortKey | null; direction: 'asc' | 'desc' }>({ key: null, direction: 'asc' });

const provisional = computed(() => {
  const keys = new Set<string>();
  groups.value?.forEach((g) => g.grades?.provisionalMilestones.forEach((k) => keys.add(k)));
  return keys;
});

const pending = computed(() => groups.value?.some((g) => g.status === 'pending' && g.students.length > 0) ?? true);

function header(key: string, i: number) {
  return milestoneHeader(i, provisional.value.has(key));
}

function groupError(group: GroupState): string {
  return errorMessage(group.failure, t('grades.groupError'));
}

function gradeOf(row: Row, key: GradeKey): number | null {
  const s = row.group.grades?.students.find((st) => st.login.toLowerCase() === row.login.toLowerCase());
  const value = s ? s[key] : null;
  return value === undefined ? null : value;
}

function cellText(row: Row, key: GradeKey): string {
  if (row.group.status === 'pending') return t('grades.loadingCell');
  if (row.group.status === 'error') return '—';
  const value = gradeOf(row, key);
  return formatGrade(value) + (value !== null && key !== 'total' && provisional.value.has(key) ? t('common.inProgress') : '');
}

/** Number of the cell for sorting and tests ("" when missing), as the data-value of the JSP. */
function dataValue(row: Row, key: GradeKey): string {
  if (row.group.status !== 'done') return '';
  const value = gradeOf(row, key);
  return value === null ? '' : String(value);
}

function notes(row: Row): string {
  if (row.group.status === 'error') return groupError(row.group);
  const excluded = row.group.grades?.excludedCommits ?? 0;
  return excluded > 0 ? t('grades.notCounted', { count: excluded }) : '';
}

const rows = computed<Row[]>(() => (groups.value ?? []).flatMap((g) => (g.students.length === 0
  ? [{ key: `${g.id}:`, group: g, login: '' }]
  : g.students.map((login) => ({ key: `${g.id}:${login}`, group: g, login })))));

// filter by group or student
const visible = computed(() => {
  const text = filter.value.trim().toLowerCase();
  const list = rows.value.filter((r) => !text || r.group.name.toLowerCase().includes(text) || r.login.toLowerCase().includes(text));
  if (!sort.key) return list;
  const key = sort.key;
  const sign = sort.direction === 'asc' ? 1 : -1;
  const value = (r: Row): string | number | null => {
    if (key === 'group') return r.group.name.toLowerCase();
    if (key === 'login') return r.login.toLowerCase();
    if (r.group.status !== 'done' || !r.login) return null;
    return gradeOf(r, key);
  };
  // missing grades ("—") and errors always last
  return [...list].sort((a, b) => {
    const va = value(a);
    const vb = value(b);
    const ma = va === null || va === '';
    const mb = vb === null || vb === '';
    if (ma !== mb) return ma ? 1 : -1;
    if (ma && mb) return 0;
    return va! < vb! ? -sign : va! > vb! ? sign : 0;
  });
});

function sortBy(key: SortKey) {
  sort.direction = sort.key === key && sort.direction === 'asc' ? 'desc' : 'asc';
  sort.key = key;
}

async function loadGroup(group: GroupState) {
  try {
    group.grades = await api.groupGrades(group.id);
    group.status = 'done';
  } catch (e) {
    group.failure = e;
    group.status = 'error';
  }
}

function exportCsv() {
  const data: CsvGroup[] = (groups.value ?? []).map((g) => ({
    name: g.name,
    students: g.students,
    grades: g.grades ? new Map(g.grades.students.map((s) => [s.login.toLowerCase(), s])) : null,
    provisionalMilestones: g.grades?.provisionalMilestones ?? [],
    error: g.status === 'error' ? groupError(g) : null,
  }));
  download('grades.csv', gradesCsv(data));
}

onMounted(async () => {
  try {
    const book = await api.gradebook();
    groups.value = book.groups.map((g) => ({ ...g, status: 'pending', grades: null, failure: null }));
  } catch (e) {
    const message = () => errorMessage(e, t('grades.loadError'));
    if (e instanceof ApiError && e.status === 403) forbidden.value = message;
    else loadError.value = message;
    return;
  }
  // progressive load: groups in page order, PARALLEL at a time
  const queue = groups.value.filter((g) => g.students.length > 0);
  const worker = async (): Promise<void> => {
    const next = queue.shift();
    if (!next) return;
    await loadGroup(next);
    return worker();
  };
  await Promise.all(Array.from({ length: PARALLEL }, worker));
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: t('common.home'), to: '/home' }, { label: t('grades.title') }]" />

    <div v-if="forbidden" class="alert alert-danger" role="alert" id="grades-forbidden">{{ forbidden() }}</div>
    <div v-else-if="loadError" class="alert alert-danger" role="alert" id="grades-error">{{ loadError() }}</div>

    <template v-else>
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="mb-0">{{ t('grades.title') }}</h4>
        <div class="d-flex flex-wrap gap-2">
          <input v-model="filter" type="search" class="form-control form-control-sm w-auto grades-filter" id="grades-filter"
                 :placeholder="t('grades.filter')" :aria-label="t('grades.filter')">
          <button type="button" class="btn btn-sm btn-outline-primary" id="export-csv" :disabled="pending"
                  :title="t(pending ? 'grades.exportWait' : 'grades.exportReady')" @click="exportCsv">{{ t('grades.export') }}</button>
        </div>
      </div>

      <p class="small text-body-secondary" id="grades-help">
        {{ t('grades.help') }}
      </p>

      <div v-if="groups === null" class="gc-loader" role="status" :aria-label="t('common.loading')"></div>

      <div v-else class="table-responsive">
        <table class="table table-sm table-hover align-middle" id="grades-table">
          <thead>
            <tr>
              <th scope="col"><button type="button" class="sort-button" data-sort="group" :data-direction="sort.key === 'group' ? sort.direction : undefined" @click="sortBy('group')">{{ t('grades.colGroup') }}</button></th>
              <th scope="col"><button type="button" class="sort-button" data-sort="login" :data-direction="sort.key === 'login' ? sort.direction : undefined" @click="sortBy('login')">{{ t('grades.colStudent') }}</button></th>
              <th v-for="(key, i) in MILESTONE_KEYS" :key="key" scope="col">
                <button type="button" class="sort-button" :data-sort="key" :id="`header-${key}`" :data-direction="sort.key === key ? sort.direction : undefined" @click="sortBy(key)">{{ header(key, i) }}</button>
              </th>
              <th scope="col"><button type="button" class="sort-button" data-sort="total" :data-direction="sort.key === 'total' ? sort.direction : undefined" @click="sortBy('total')">{{ t('grading.totalColumn') }}</button></th>
              <th scope="col">{{ t('grades.colNotes') }}</th>
            </tr>
          </thead>
          <tbody id="grades-tbody">
            <tr v-for="row in visible" :key="row.key" class="grade-row"
                :class="{ pending: row.group.status === 'pending' && row.login, 'group-error': row.group.status === 'error', 'empty-group': !row.login }"
                :data-group-id="row.group.id" :data-group-name="row.group.name" :data-login="row.login">
              <td><RouterLink :to="{ path: '/detail', query: { id: String(row.group.id) } }">{{ row.group.name }}</RouterLink></td>
              <td v-if="!row.login" colspan="6" class="text-body-secondary">{{ t('grades.noStudents') }}</td>
              <template v-else>
                <td class="student-login">{{ row.login }}</td>
                <td v-for="key in MILESTONE_KEYS" :key="key" class="grade-cell" :data-key="key" :data-value="dataValue(row, key)">{{ cellText(row, key) }}</td>
                <td class="grade-cell fw-semibold" data-key="total" :data-value="dataValue(row, 'total')">{{ cellText(row, 'total') }}</td>
                <td class="group-notes small" :class="{ 'text-danger': row.group.status === 'error', 'text-warning-emphasis': row.group.status === 'done' && (row.group.grades?.excludedCommits ?? 0) > 0 }">{{ notes(row) }}</td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="groups && groups.length === 0" class="text-center text-body-secondary" id="no-groups">{{ t('grades.empty') }}</p>
    </template>
  </main>
</template>

<style scoped>
.sort-button { border: 0; background: none; padding: 0; font-weight: 600; color: inherit; }
.sort-button[data-direction="asc"]::after { content: " \25B2"; }
.sort-button[data-direction="desc"]::after { content: " \25BC"; }
.grade-cell { white-space: nowrap; }
.grades-filter { min-width: 16rem; }
</style>
