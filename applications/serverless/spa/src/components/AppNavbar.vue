<script setup lang="ts">
import { useRouter } from 'vue-router';
import { api } from '@/api';
import { useSession } from '@/composables/useSession';

/**
 * Navbar of every authenticated page (openspec capability navigation-bar): brand (home), administrator links,
 * login and "Sign out". The menu collapses on phones (Bootstrap collapse, Bootstrap JS loaded once in main.ts).
 */
defineProps<{ active?: 'groups' | 'grades' }>();

const { session, clear } = useSession();
const router = useRouter();

async function signOut() {
  try {
    await api.signOut();
  } catch {
    // the session item expires anyway; the browser forgets it below
  }
  clear();
  router.push({ name: 'login', query: { signedOut: '1' } });
}
</script>

<template>
  <nav class="navbar navbar-expand-md bg-body-tertiary border-bottom" aria-label="Main navigation" id="main-navbar">
    <div class="container">
      <RouterLink class="navbar-brand" to="/home" id="navbar-brand">Grade Checker</RouterLink>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#main-navbar-items"
              aria-controls="main-navbar-items" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="main-navbar-items">
        <ul class="navbar-nav me-auto">
          <template v-if="session.me?.isAdmin">
            <li class="nav-item">
              <RouterLink class="nav-link" :class="{ active: active === 'groups' }" to="/groups" id="manage-groups-link"
                          :aria-current="active === 'groups' ? 'page' : undefined">Manage groups</RouterLink>
            </li>
            <li class="nav-item">
              <RouterLink class="nav-link" :class="{ active: active === 'grades' }" to="/grades" id="class-grades-link"
                          :aria-current="active === 'grades' ? 'page' : undefined">Class grades</RouterLink>
            </li>
          </template>
        </ul>
        <ul class="navbar-nav align-items-md-center">
          <li v-if="session.me?.login" class="nav-item">
            <span class="navbar-text me-md-3" id="navbar-user">{{ session.me.login }}</span>
          </li>
          <li class="nav-item">
            <a class="nav-link" href="/login" id="sign-out-link" @click.prevent="signOut">Sign out</a>
          </li>
        </ul>
      </div>
    </div>
  </nav>
</template>
