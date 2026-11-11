<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { errorMessage } from '@/api/client';
import type { GroupSummary } from '@/api/types';
import { useSession } from '@/composables/useSession';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';

/** Home: the students' page, a grid of the active groups (openspec capability card-grid-layout). */
const { session } = useSession();
const { t } = useI18n();
const groups = ref<GroupSummary[] | null>(null);
/** the text is computed on render, so it follows a change of language */
const error = ref<(() => string) | null>(null);

onMounted(async () => {
  try {
    groups.value = await api.groups();
  } catch (e) {
    error.value = () => errorMessage(e, t('home.loadError'));
  }
});
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: t('common.home'), to: '/home' }]" />

    <h4>{{ t('home.groups') }}</h4>

    <div v-if="error" class="alert alert-danger" role="alert" id="home-error">{{ error() }}</div>
    <div v-else-if="groups === null" class="gc-loader" role="status" :aria-label="t('common.loading')"></div>

    <template v-else-if="groups.length === 0">
      <p class="text-center no-groups-message" id="no-groups">{{ t('home.empty') }}</p>
      <div v-if="session.me?.isAdmin" class="text-center">
        <RouterLink :to="{ path: '/groups', query: { add: '1' } }" id="add-group-home-link">{{ t('home.addGroup') }}</RouterLink>
      </div>
    </template>

    <!-- 1 column < 576px, 2 columns 576-991px, 3 columns >= 992px; cards fill their column -->
    <div v-else class="row row-cols-1 row-cols-sm-2 row-cols-lg-3 g-4 mb-4">
      <div v-for="group in groups" :key="group.id" class="col">
        <div class="card h-100 shadow-sm">
          <img class="card-img-top group-card-img" src="/img/group.png" :alt="t('common.group', { id: group.id })">
          <div class="card-body text-center">
            <h5 class="card-title">{{ t('common.group', { id: group.id }) }}</h5>
          </div>
          <ul class="list-group list-group-flush">
            <li class="list-group-item"><b>{{ t('home.nameLabel') }}</b> {{ group.name }}</li>
          </ul>
          <div class="card-footer bg-transparent border-0 text-center py-3">
            <RouterLink :to="{ path: '/detail', query: { id: String(group.id) } }" class="card-link">{{ t('common.details') }}</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
/* same illustration as before, smaller and never cropped */
.group-card-img { height: 160px; object-fit: contain; padding: 1rem 1rem 0; }
.no-groups-message { margin-top: 6rem; }
</style>
