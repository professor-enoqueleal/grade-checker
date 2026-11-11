<script setup lang="ts">
import { computed, watchEffect } from 'vue';
import { useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import AppNavbar from '@/components/AppNavbar.vue';

const route = useRoute();
const { t } = useI18n();
// every authenticated page has the navbar; the sign-in page has none
const showNavbar = computed(() => route.name !== undefined && !route.meta.public);
// tab title: follows the page and the language
watchEffect(() => {
  if (route.meta.title) document.title = t(route.meta.title);
});
</script>

<template>
  <AppNavbar v-if="showNavbar" :active="route.meta.nav" />
  <RouterView :key="route.fullPath" />
</template>
