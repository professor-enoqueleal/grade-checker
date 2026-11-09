<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/client';
import type { ArchivedGroup, GroupSummary } from '@/api/types';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import GroupFormModal from '@/components/GroupFormModal.vue';
import { gitHubLink, repositoryLabel } from '@/utils/format';

/**
 * "Manage groups" (openspec capability group-management), administrators only: active groups (Edit, Archive) and
 * archived groups (Restore). /groups?add=1 (from the empty home) opens the form.
 */
const route = useRoute();
const groups = ref<GroupSummary[] | null>(null);
const archived = ref<ArchivedGroup[]>([]);
const pageError = ref('');
const alert = ref('');
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
    alert.value = errorMessage(e, 'Could not load the groups. Please try again.');
  }
}

async function change(action: 'archive' | 'restore', id: number) {
  alert.value = '';
  try {
    await (action === 'archive' ? api.archiveGroup(id) : api.restoreGroup(id));
    await reload();
  } catch (e) {
    alert.value = errorMessage(e, `Could not ${action} the group. Please try again.`);
  }
}

function archive(group: GroupSummary) {
  if (!window.confirm(`Archive ${group.name}? It disappears from the home and the class grades; members and linked identities are kept.`)) return;
  change('archive', group.id);
}

onMounted(async () => {
  try {
    await load();
  } catch (e) {
    pageError.value = e instanceof ApiError && e.status === 403 ? e.message
      : errorMessage(e, 'Could not load the groups. Please try again.');
    return;
  }
  if (route.query.add === '1') form.value?.open();
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: 'Home', to: '/home' }, { label: 'Manage groups' }]" />

    <div v-if="pageError" class="alert alert-danger" role="alert" id="groups-page-error">{{ pageError }}</div>

    <template v-else>
      <div id="content">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <h4 class="mb-0">Manage groups</h4>
          <a href="#" id="add-group-link" @click.prevent="form?.open()">Add a new group</a>
        </div>

        <div v-if="alert" id="groups-alert" class="alert alert-danger" role="alert">{{ alert }}</div>

        <div v-if="groups === null" class="gc-loader" role="status" aria-label="Loading"></div>
        <p v-else-if="groups.length === 0" class="text-body-secondary" id="no-groups">No groups yet.</p>
        <div v-else class="table-responsive">
          <table class="table table-sm table-hover align-middle" id="groups-table">
            <thead>
              <tr>
                <th scope="col">Group</th>
                <th scope="col">Name</th>
                <th scope="col" class="d-none d-sm-table-cell">Repository</th>
                <th scope="col" class="d-none d-sm-table-cell">Members</th>
                <th scope="col">Actions</th>
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
                  <a href="#" class="edit-group-link" :data-group-id="group.id" @click.prevent="form?.open(group)">Edit</a>
                  <a href="#" class="archive-group-link" :data-group-id="group.id" @click.prevent="archive(group)">Archive</a>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template v-if="archived.length > 0">
          <h5 class="mt-4" id="archived-groups-title">Archived groups</h5>
          <div class="table-responsive">
            <table class="table table-sm align-middle text-body-secondary" id="archived-groups-table">
              <thead>
                <tr>
                  <th scope="col">Group</th>
                  <th scope="col">Name</th>
                  <th scope="col" class="d-none d-sm-table-cell">Repository</th>
                  <th scope="col" class="d-none d-sm-table-cell">Archived on</th>
                  <th scope="col">Actions</th>
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
                    <a href="#" class="restore-group-link" :data-group-id="group.id" @click.prevent="change('restore', group.id)">Restore</a>
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
