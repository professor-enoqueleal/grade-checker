<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/client';
import type { GroupMembersResponse, Member } from '@/api/types';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import MemberFormModal from '@/components/MemberFormModal.vue';

/** "Manage members" (openspec capability group-members), administrators only: list, add, edit (name and login), remove. */
const route = useRoute();
const groupId = Number(route.query.id);
const valid = Number.isInteger(groupId) && groupId > 0;

const data = ref<GroupMembersResponse | null>(null);
const pageError = ref('');
const alert = ref('');
const form = ref<InstanceType<typeof MemberFormModal> | null>(null);

async function load() {
  data.value = await api.members(groupId);
}

async function reload() {
  try {
    await load();
  } catch (e) {
    alert.value = errorMessage(e, 'Could not load the members. Please try again.');
  }
}

async function remove(member: Member) {
  if (!window.confirm(`Remove ${member.login} from the members of this group? Their commits will no longer receive a grade.`)) return;
  alert.value = '';
  try {
    await api.removeMember(groupId, member.login);
    await reload();
  } catch (e) {
    alert.value = errorMessage(e, 'Could not remove the member. Please try again.');
  }
}

onMounted(async () => {
  if (!valid) {
    pageError.value = "The 'id' parameter is required.";
    return;
  }
  try {
    await load();
  } catch (e) {
    pageError.value = e instanceof ApiError && e.status === 404 ? 'Group not found.'
      : errorMessage(e, 'Could not load the members. Please try again.');
  }
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="valid
      ? [{ label: 'Home', to: '/home' }, { label: `Group ${groupId}`, to: { path: '/detail', query: { id: String(groupId) } }, id: 'breadcrumb-group' }, { label: 'Manage members' }]
      : [{ label: 'Home', to: '/home' }, { label: 'Manage members' }]" />

    <div v-if="pageError" class="alert alert-danger" role="alert" id="members-page-error">{{ pageError }}</div>
    <div v-else-if="!data" class="gc-loader" role="status" aria-label="Loading"></div>

    <template v-else>
      <div id="content" :data-group-id="groupId">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <h4 class="mb-0">Manage members <small class="text-body-secondary fs-6" id="group-name">{{ data.group.name }}</small></h4>
          <a href="#" id="add-member-link" @click.prevent="form?.open()">Add a new member</a>
        </div>

        <div v-if="alert" id="members-alert" class="alert alert-danger" role="alert">{{ alert }}</div>

        <p v-if="data.members.length === 0" class="text-body-secondary" id="no-members">No members yet.</p>
        <div v-else class="table-responsive">
          <table class="table table-sm table-hover align-middle" id="members-table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">GitHub login</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in data.members" :key="member.login" :data-login="member.login">
                <td class="member-name">{{ member.name }}</td>
                <td class="member-login">
                  <a :href="`https://github.com/${member.login}`" target="_blank" rel="noopener">{{ member.login }}</a>
                </td>
                <td class="member-actions">
                  <a href="#" class="edit-member-link" :data-login="member.login" :data-name="member.name" @click.prevent="form?.open(member)">Edit</a>
                  <a href="#" class="remove-member-link" :data-login="member.login" @click.prevent="remove(member)">Remove</a>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <MemberFormModal ref="form" :group-id="groupId" @saved="reload" />
    </template>
  </main>
</template>

<style scoped>
.member-actions a + a { margin-left: 1rem; }
</style>
