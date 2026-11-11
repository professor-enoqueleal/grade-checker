<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { useSession } from '@/composables/useSession';
import LanguageSelector from '@/components/LanguageSelector.vue';

/**
 * Navbar of every authenticated page (openspec capability serverless-navigation-bar): brand (home), administrator links,
 * language selector and the user menu (avatar and login; inside: "Signed in as", the role and "Sign out"). Below 768px
 * everything collapses into one menu (Bootstrap collapse, Bootstrap JS loaded once in main.ts): the user button is hidden
 * and its items show as plain lines of that menu, with no menu inside the menu. The language selector stays outside the
 * collapsed menu, next to the menu button, always visible; on wider screens it sits between the links and the user menu.
 */
defineProps<{ active?: 'groups' | 'grades' }>();

const { session, clear } = useSession();
const router = useRouter();
const { t } = useI18n();

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
  <nav class="navbar navbar-expand-md bg-body-tertiary border-bottom" :aria-label="t('nav.label')" id="main-navbar">
    <div class="container">
      <RouterLink class="navbar-brand" to="/home" id="navbar-brand">{{ t('app.name') }}</RouterLink>
      <div class="navbar-language ms-auto me-2 ms-md-0 me-md-3 order-md-1">
        <LanguageSelector />
      </div>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#main-navbar-items"
              aria-controls="main-navbar-items" aria-expanded="false" :aria-label="t('nav.toggle')">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="main-navbar-items">
        <ul class="navbar-nav me-auto">
          <template v-if="session.me?.isAdmin">
            <li class="nav-item">
              <RouterLink class="nav-link" :class="{ active: active === 'groups' }" to="/groups" id="manage-groups-link"
                          :aria-current="active === 'groups' ? 'page' : undefined">{{ t('nav.manageGroups') }}</RouterLink>
            </li>
            <li class="nav-item">
              <RouterLink class="nav-link" :class="{ active: active === 'grades' }" to="/grades" id="class-grades-link"
                          :aria-current="active === 'grades' ? 'page' : undefined">{{ t('nav.classGrades') }}</RouterLink>
            </li>
          </template>
        </ul>
        <ul class="navbar-nav align-items-md-center order-md-2">
          <li class="nav-item dropdown">
            <button class="nav-link dropdown-toggle d-none d-md-flex align-items-center gap-2" type="button" id="user-menu"
                    data-bs-toggle="dropdown" aria-expanded="false" :aria-label="session.me?.login ? undefined : t('nav.userMenu')">
              <img v-if="session.me?.avatarUrl" :src="session.me.avatarUrl" class="rounded-circle user-avatar" width="24" height="24"
                   alt="" id="navbar-avatar">
              <span v-if="session.me?.login" id="navbar-user">{{ session.me.login }}</span>
            </button>
            <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="user-menu" id="user-menu-items">
              <li v-if="session.me?.login">
                <span class="dropdown-item-text text-nowrap d-flex align-items-center gap-2" id="user-menu-signed-in">
                  <img v-if="session.me.avatarUrl" :src="session.me.avatarUrl" class="rounded-circle user-avatar d-md-none" width="24"
                       height="24" alt="">
                  <span>{{ t('nav.signedInAs') }} <strong>{{ session.me.login }}</strong></span>
                </span>
              </li>
              <li v-if="session.me">
                <span class="dropdown-item-text small text-body-secondary" id="user-menu-role">
                  {{ session.me.isAdmin ? t('nav.administrator') : t('nav.student') }}
                </span>
              </li>
              <li v-if="session.me"><hr class="dropdown-divider"></li>
              <li><a class="dropdown-item" href="/login" id="sign-out-link" @click.prevent="signOut">{{ t('nav.signOut') }}</a></li>
            </ul>
          </li>
        </ul>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.user-avatar {
  object-fit: cover;
}
/* wide screens: the two lists of the collapsed menu become items of the bar itself, so the language selector (outside the
   collapsed menu) sits between them: links, flags, user menu */
@media (min-width: 768px) {
  #main-navbar-items {
    display: contents !important;
  }
}
/* collapsed (phone): the items of the user menu are lines of the navbar menu, always shown while it is open */
@media (max-width: 767.98px) {
  #user-menu-items {
    display: block;
    position: static;
    padding: 0 0 0.5rem;
    border: 0;
    background: transparent;
    --bs-dropdown-item-padding-x: 0;
  }
}
</style>
