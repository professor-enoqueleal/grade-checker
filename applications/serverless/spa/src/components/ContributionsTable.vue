<script setup lang="ts">
import { computed } from 'vue';
import type { ContributionRow } from '@/api/types';
import { formatPercentage } from '@/utils/format';
import type { GradeColumn } from '@/utils/grades';

/**
 * Table of the dashboard: one row per author, grade columns per period, members without commits with zeros.
 * Authors without a GitHub account get the "No GitHub account" badge; administrators can link them to a student
 * (openspec capability identity-aliases).
 */
const props = defineProps<{
  rows: ContributionRow[];
  columns: GradeColumn[];
  members: string[];
  isAdmin: boolean;
}>();
const emit = defineEmits<{ link: [identityKey: string, label: string]; unlink: [identityKey: string, label: string] }>();

/** members of the group without commits in the period (zeros) */
const idle = computed(() => {
  const seen = new Set(props.rows.map((r) => r.author));
  return props.members.filter((login) => !seen.has(login));
});

function identities(row: ContributionRow): string {
  const manual = new Set(row.manualIdentities.map((m) => m.identity));
  return row.gitIdentities.map((i) => (manual.has(i) ? i + ' (manual)' : i)).join(', ');
}
</script>

<template>
  <div class="table-responsive">
    <table class="table table-hover contributions-table">
      <thead>
        <tr>
          <th scope="col">Author</th>
          <th scope="col">Commits</th>
          <th scope="col">Additions</th>
          <th scope="col">Deletions</th>
          <th scope="col">Percentage</th>
          <th v-for="c in columns" :key="c.header" scope="col" class="grade-col">{{ c.header }}</th>
        </tr>
      </thead>
      <tbody id="contributions-tbody">
        <tr v-for="row in rows" :key="row.author + (row.identityKey ?? '')" :data-login="row.author">
          <td>
            <span class="author-name">{{ row.author }}</span>
            <template v-if="row.linked === false">
              <span class="badge text-bg-warning fw-normal ms-2 no-account-badge"
                    :title="`Commits not linked to a GitHub account: ${identities(row)}`">No GitHub account</span>
              <button v-if="isAdmin && row.identityKey" type="button" class="btn btn-sm btn-outline-primary py-0 ms-2 link-identity-button"
                      :data-identity-key="row.identityKey" :data-identity-label="row.author"
                      @click="emit('link', row.identityKey, row.author)">Link to student</button>
            </template>
            <span v-else-if="identities(row)" class="badge text-bg-secondary fw-normal ms-2 includes-unlinked-badge"
                  :title="`Also counted here (commits not linked to the account): ${identities(row)}`">Includes unlinked commits</span>
            <div v-for="m in row.manualIdentities" :key="m.key" class="small text-body-secondary manual-identity">
              Linked manually: {{ m.identity }}
              <button v-if="isAdmin" type="button" class="btn btn-link btn-sm p-0 ms-2 align-baseline unlink-identity-button"
                      :data-identity-key="m.key" :data-identity-label="m.identity"
                      @click="emit('unlink', m.key, m.identity)">Unlink</button>
            </div>
          </td>
          <td>{{ row.commits }}</td>
          <td>{{ row.totalAdditions }}</td>
          <td>{{ row.totalDeletions }}</td>
          <td>{{ formatPercentage(row.percentage) }}</td>
          <td v-for="c in columns" :key="c.header" class="grade-cell fw-semibold" :title="c.cell(row).title">{{ c.cell(row).text }}</td>
        </tr>
        <tr v-for="login in idle" :key="'idle-' + login" :data-login="login" class="text-body-secondary">
          <td>{{ login }}</td>
          <td>0</td>
          <td>0</td>
          <td>0</td>
          <td>0.00%</td>
          <td v-for="c in columns" :key="c.header" class="grade-cell">—</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.contributions-table { margin-top: 0.5rem; }
</style>
