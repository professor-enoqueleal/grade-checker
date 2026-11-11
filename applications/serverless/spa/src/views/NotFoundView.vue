<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';

const { t } = useI18n();
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: t('common.home'), to: '/home' }, { label: t('notFound.crumb') }]" />
    <div class="alert alert-warning" role="alert" id="not-found">{{ t('notFound.message') }}</div>
    <RouterLink to="/home">{{ t('notFound.back') }}</RouterLink>
  </main>
</template>
