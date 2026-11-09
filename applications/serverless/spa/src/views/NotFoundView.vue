<script setup lang="ts">
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
</script>

<template>
  <main class="container">
    <AppBreadcrumb :items="[{ label: 'Home', to: '/home' }, { label: 'Page not found' }]" />
    <div class="alert alert-warning" role="alert" id="not-found">This page does not exist.</div>
    <RouterLink to="/home">Back to the groups</RouterLink>
  </main>
</template>
