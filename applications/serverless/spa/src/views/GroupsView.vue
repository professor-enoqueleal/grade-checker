<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import type { ArchivedGroup, GroupSummary } from '@/api/types';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import GroupFormModal from '@/components/GroupFormModal.vue';
import { gitHubLink, repositoryLabel } from '@/utils/format';

/**
 * "Manage groups" (openspec capability group-management), administrators only: active groups (Edit, Archive) and
 * archived groups (Restore). /groups?add=1 (from the empty home) opens the form.
 */
const route = useRoute();
const { t } = useI18n();
const groups = ref<GroupSummary[] | null>(null);
const archived = ref<ArchivedGroup[]>([]);
// messages are computed on render, so they follow a change of language
const pageError = ref<(() => string) | null>(null);
const alert = ref<(() => string) | null>(null);
const form = ref<InstanceType<typeof GroupFormModal> | null>(null);

async function load() {
  const [active, old] = await Promise.all([api.groups(), api.archivedGroups()]);
  groups.value = active;
  archived.value = old.groups;
}

async function reload() {
  try {
    await load();
  } catch (e) {
    alert.value = () => errorMessage(e, t('groups.loadError'));
  }
}

async function change(action: 'archive' | 'restore', id: number) {
  alert.value = null;
  try {
    await (action === 'archive' ? api.archiveGroup(id) : api.restoreGroup(id));
    await reload();
  } catch (e) {
    alert.value = () => errorMessage(e, t(action === 'archive' ? 'groups.archiveError' : 'groups.restoreError'));
  }
}

function archive(group: GroupSummary) {
  if (!window.confirm(t('groups.confirmArchive', { name: group.name }))) return;
  change('archive', group.id);
}

onMounted(async () => {
  try {
    await load();
  } catch (e) {
    pageError.value = () => errorMessage(e, t('groups.loadError'));
    return;
  }
  if (route.query.add === '1') form.value?.open();
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: t('common.home'), to: '/home' }, { label: t('groups.title') }]" />

    <div v-if="pageError" class="alert alert-danger" role="alert" id="groups-page-error">{{ pageError() }}</div>

    <template v-else>
      <div id="content">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <h4 class="mb-0">{{ t('groups.title') }}</h4>
          <a href="#" id="add-group-link" @click.prevent="form?.open()">{{ t('groups.addGroup') }}</a>
        </div>

        <div v-if="alert" id="groups-alert" class="alert alert-danger" role="alert">{{ alert() }}</div>

        <div v-if="groups === null" class="gc-loader" role="status" :aria-label="t('common.loading')"></div>
        <p v-else-if="groups.length === 0" class="text-body-secondary" id="no-groups">{{ t('groups.empty') }}</p>
        <div v-else class="table-responsive">
          <table class="table table-sm table-hover align-middle" id="groups-table">
            <thead>
              <tr>
                <th scope="col">{{ t('groups.colGroup') }}</th>
                <th scope="col">{{ t('common.name') }}</th>
                <th scope="col" class="d-none d-sm-table-cell">{{ t('groups.colRepository') }}</th>
                <th scope="col" class="d-none d-sm-table-cell">{{ t('groups.colMembers') }}</th>
                <th scope="col">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="group in groups" :key="group.id" :data-group-id="group.id">
                <td class="group-id"><RouterLink :to="{ path: '/detail', query: { id: String(group.id) } }">{{ group.id }}</RouterLink></td>
                <td>
                  <span class="group-name">{{ group.name }}</span>
                  <!-- phones: the Repository column is hidden and the repository goes below the name -->
                  <div class="repository-inline d-sm-none small">{{ repositoryLabel(group.repositoryUrl) }}</div>
                </td>
                <td class="repository-cell d-none d-sm-table-cell">
                  <a v-if="gitHubLink(group.repositoryUrl)" :href="group.repositoryUrl" target="_blank" rel="noopener noreferrer"
                     :title="group.repositoryUrl">{{ repositoryLabel(group.repositoryUrl) }}</a>
                  <template v-else>{{ repositoryLabel(group.repositoryUrl) }}</template>
                </td>
                <td class="group-members d-none d-sm-table-cell">{{ group.members }}</td>
                <td class="group-actions text-nowrap">
                  <a href="#" class="edit-group-link" :data-group-id="group.id" @click.prevent="form?.open(group)">{{ t('common.edit') }}</a>
                  <a href="#" class="archive-group-link" :data-group-id="group.id" @click.prevent="archive(group)">{{ t('groups.archive') }}</a>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template v-if="archived.length > 0">
          <h5 class="mt-4" id="archived-groups-title">{{ t('groups.archivedTitle') }}</h5>
          <div class="table-responsive">
            <table class="table table-sm align-middle text-body-secondary" id="archived-groups-table">
              <thead>
                <tr>
                  <th scope="col">{{ t('groups.colGroup') }}</th>
                  <th scope="col">{{ t('common.name') }}</th>
                  <th scope="col" class="d-none d-sm-table-cell">{{ t('groups.colRepository') }}</th>
                  <th scope="col" class="d-none d-sm-table-cell">{{ t('groups.colArchivedOn') }}</th>
                  <th scope="col">{{ t('common.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="group in archived" :key="group.id" :data-group-id="group.id">
                  <td>{{ group.id }}</td>
                  <td>
                    <span class="group-name">{{ group.name }}</span>
                    <div class="repository-inline d-sm-none small">{{ repositoryLabel(group.repositoryUrl) }}</div>
                  </td>
                  <td class="repository-cell d-none d-sm-table-cell" :title="group.repositoryUrl">{{ repositoryLabel(group.repositoryUrl) }}</td>
                  <td class="d-none d-sm-table-cell">{{ group.archivedOn }}</td>
                  <td class="group-actions text-nowrap">
                    <a href="#" class="restore-group-link" :data-group-id="group.id" @click.prevent="change('restore', group.id)">{{ t('groups.restore') }}</a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>

      <GroupFormModal ref="form" @saved="reload" />
    </template>
  </main>
</template>

<style scoped>
.group-actions a + a { margin-left: 1rem; }
.repository-cell, .repository-inline { overflow-wrap: anywhere; }
</style>
