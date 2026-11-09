<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppNavbar from '@/components/AppNavbar.vue';

const route = useRoute();
// every authenticated page has the navbar; the sign-in page has none
const showNavbar = computed(() => route.name !== undefined && !route.meta.public);
</script>

<template>
  <AppNavbar v-if="showNavbar" :active="route.meta.nav" />
  <RouterView :key="route.fullPath" />
</template>
