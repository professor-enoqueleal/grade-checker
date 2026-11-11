<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { ApiError, errorMessage } from '@/api/client';
import type { GroupMembersResponse, Member } from '@/api/types';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import MemberFormModal from '@/components/MemberFormModal.vue';

/** "Manage members" (openspec capability group-members), administrators only: list, add, edit (name and login), remove. */
const route = useRoute();
const { t } = useI18n();
const groupId = Number(route.query.id);
const valid = Number.isInteger(groupId) && groupId > 0;

const data = ref<GroupMembersResponse | null>(null);
// messages are computed on render, so they follow a change of language
const pageError = ref<(() => string) | null>(null);
const alert = ref<(() => string) | null>(null);
const form = ref<InstanceType<typeof MemberFormModal> | null>(null);

async function load() {
  data.value = await api.members(groupId);
}

async function reload() {
  try {
    await load();
  } catch (e) {
    alert.value = () => errorMessage(e, t('members.loadError'));
  }
}

async function remove(member: Member) {
  if (!window.confirm(t('members.confirmRemove', { login: member.login }))) return;
  alert.value = null;
  try {
    await api.removeMember(groupId, member.login);
    await reload();
  } catch (e) {
    alert.value = () => errorMessage(e, t('members.removeError'));
  }
}

onMounted(async () => {
  if (!valid) {
    pageError.value = () => t('common.idRequired');
    return;
  }
  try {
    await load();
  } catch (e) {
    pageError.value = e instanceof ApiError && e.status === 404 ? () => t('common.groupNotFound')
      : () => errorMessage(e, t('members.loadError'));
  }
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="valid
      ? [{ label: t('common.home'), to: '/home' }, { label: t('common.group', { id: groupId }), to: { path: '/detail', query: { id: String(groupId) } }, id: 'breadcrumb-group' }, { label: t('members.title') }]
      : [{ label: t('common.home'), to: '/home' }, { label: t('members.title') }]" />

    <div v-if="pageError" class="alert alert-danger" role="alert" id="members-page-error">{{ pageError() }}</div>
    <div v-else-if="!data" class="gc-loader" role="status" :aria-label="t('common.loading')"></div>

    <template v-else>
      <div id="content" :data-group-id="groupId">
        <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <h4 class="mb-0">{{ t('members.title') }} <small class="text-body-secondary fs-6" id="group-name">{{ data.group.name }}</small></h4>
          <a href="#" id="add-member-link" @click.prevent="form?.open()">{{ t('members.addMember') }}</a>
        </div>

        <div v-if="alert" id="members-alert" class="alert alert-danger" role="alert">{{ alert() }}</div>

        <p v-if="data.members.length === 0" class="text-body-secondary" id="no-members">{{ t('members.empty') }}</p>
        <div v-else class="table-responsive">
          <table class="table table-sm table-hover align-middle" id="members-table">
            <thead>
              <tr>
                <th scope="col">{{ t('common.name') }}</th>
                <th scope="col">{{ t('members.colLogin') }}</th>
                <th scope="col">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in data.members" :key="member.login" :data-login="member.login">
                <td class="member-name">{{ member.name }}</td>
                <td class="member-login">
                  <a :href="`https://github.com/${member.login}`" target="_blank" rel="noopener">{{ member.login }}</a>
                </td>
                <td class="member-actions">
                  <a href="#" class="edit-member-link" :data-login="member.login" :data-name="member.name" @click.prevent="form?.open(member)">{{ t('common.edit') }}</a>
                  <a href="#" class="remove-member-link" :data-login="member.login" @click.prevent="remove(member)">{{ t('members.remove') }}</a>
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
