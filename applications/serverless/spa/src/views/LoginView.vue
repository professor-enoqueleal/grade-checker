<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { api } from '@/api';
import { ApiError } from '@/api/client';
import { useSession } from '@/composables/useSession';
import LanguageSelector from '@/components/LanguageSelector.vue';
import ThemeToggle from '@/components/ThemeToggle.vue';

/**
 * Sign-in page (capabilities github-sign-in, token-sign-in, sign-out). "Continue with GitHub" is a full navigation to
 * /api/auth/github; the backend comes back here with ?error=… when the GitHub sign-in fails, or after signing out.
 */
const route = useRoute();
const router = useRouter();
const { signedIn } = useSession();
const { t } = useI18n();

/** codes of /login?error=… (GitHub sign-in); an unknown code shows the generic failure */
const CALLBACK_ERRORS = ['github_not_verified', 'github_failed'] as const;
const callbackError = (code: string) =>
  `login.callbackErrors.${(CALLBACK_ERRORS as readonly string[]).includes(code) ? code : 'github_failed'}`;

// the text is a key, so the alert follows a change of language
const message = ref<{ type: 'success' | 'danger'; key: string } | null>(
  route.query.signedOut ? { type: 'success', key: 'login.signedOut' }
    : route.query.signIn === 'required' ? { type: 'danger', key: 'login.signInRequired' }
    : typeof route.query.error === 'string' ? { type: 'danger', key: callbackError(route.query.error) }
      : null,
);
const tokenOpen = ref(false);
const token = ref('');
const sending = ref(false);
const tokenInput = ref<HTMLInputElement | null>(null);

async function toggleToken() {
  tokenOpen.value = !tokenOpen.value;
  if (tokenOpen.value) {
    await nextTick();
    tokenInput.value?.focus();
  }
}

async function signInWithToken() {
  if (!token.value.trim()) {
    message.value = { type: 'danger', key: 'login.tokenMissing' };
    tokenInput.value?.focus();
    return;
  }
  sending.value = true;
  try {
    const me = await api.signInWithToken(token.value.trim());
    signedIn(me);
    await router.push({ name: 'home' });
  } catch (e) {
    message.value = {
      type: 'danger',
      key: e instanceof ApiError && e.status === 401 ? 'login.tokenRejected' : 'login.tokenUnverified',
    };
    token.value = '';
    tokenInput.value?.focus();
  } finally {
    sending.value = false;
  }
}
</script>

<template>
  <div class="bg-body-tertiary login-page">
    <div class="login-settings position-fixed bottom-0 end-0 mb-3 me-3 d-flex align-items-center gap-2">
      <LanguageSelector />
      <ThemeToggle />
    </div>

    <main class="auth-wrapper">
      <div class="auth-card">

        <div class="text-center mb-4">
          <span class="auth-logo mb-3" aria-hidden="true"><i class="bi bi-mortarboard-fill"></i></span>
          <h1 class="h4 fw-semibold mb-1">{{ t('login.title') }}</h1>
          <p class="text-body-secondary small mb-0">{{ t('login.subtitle') }}</p>
        </div>

        <div v-if="message" class="alert alert-dismissible fade show small" :class="`alert-${message.type}`" role="alert">
          <i class="bi me-1" :class="message.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'"></i>
          {{ t(message.key) }}
          <button type="button" class="btn-close" :aria-label="t('common.close')" @click="message = null"></button>
        </div>

        <div class="card border-0 shadow-sm rounded-3">
          <div class="card-body p-4">

            <a class="btn btn-github btn-lg w-100 d-flex align-items-center justify-content-center gap-2 fs-6 py-2"
               :href="api.gitHubSignInUrl">
              <i class="bi bi-github" aria-hidden="true"></i>
              {{ t('login.continueWithGitHub') }}
            </a>

            <div class="auth-divider my-4">{{ t('login.or') }}</div>

            <button class="btn btn-link btn-token-toggle w-100 text-body-secondary text-decoration-none small d-flex align-items-center justify-content-center gap-1 p-0"
                    type="button" :aria-expanded="tokenOpen" aria-controls="token-form" @click="toggleToken">
              <i class="bi bi-key"></i>
              {{ t('login.tokenToggle') }}
              <i class="bi bi-chevron-down small"></i>
            </button>

            <div v-show="tokenOpen" id="token-form">
              <form class="pt-3" @submit.prevent="signInWithToken">
                <label for="access-token" class="form-label small fw-semibold">{{ t('login.tokenLabel') }}</label>
                <input ref="tokenInput" v-model="token" type="password" name="access-token" class="form-control" id="access-token"
                       placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" autocomplete="off" required>
                <div class="form-text">
                  {{ t('login.tokenHelpBefore') }}
                  <a href="https://github.com/settings/tokens" target="_blank" rel="noopener noreferrer">{{ t('login.tokenHelpLink') }}</a>{{ t('login.tokenHelpAfter') }}
                </div>
                <button type="submit" class="btn btn-outline-secondary w-100 mt-3" :disabled="sending">{{ t('login.tokenSubmit') }}</button>
              </form>
            </div>

          </div>
        </div>

        <p class="text-center text-body-secondary small mt-4 mb-0">
          {{ t('login.footer') }}
        </p>

      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  --gh-btn-bg: #24292f;
  --gh-btn-hover-bg: #32383f;
  --gh-btn-color: #ffffff;
  --gh-btn-border: #24292f;
  min-height: 100vh;
}
:global([data-bs-theme="dark"]) .login-page {
  --gh-btn-bg: #f6f8fa;
  --gh-btn-hover-bg: #ffffff;
  --gh-btn-color: #1f2328;
  --gh-btn-border: #f6f8fa;
}
.login-settings { z-index: 1500; }
.auth-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
}
.auth-card { width: 100%; max-width: 400px; }
.auth-logo {
  width: 56px;
  height: 56px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background-color: #712cf9;
  color: #fff;
  font-size: 1.75rem;
}
.btn-github {
  --bs-btn-color: var(--gh-btn-color);
  --bs-btn-bg: var(--gh-btn-bg);
  --bs-btn-border-color: var(--gh-btn-border);
  --bs-btn-hover-color: var(--gh-btn-color);
  --bs-btn-hover-bg: var(--gh-btn-hover-bg);
  --bs-btn-hover-border-color: var(--gh-btn-hover-bg);
  --bs-btn-active-color: var(--gh-btn-color);
  --bs-btn-active-bg: var(--gh-btn-hover-bg);
  --bs-btn-active-border-color: var(--gh-btn-hover-bg);
  --bs-btn-focus-shadow-rgb: 112, 44, 249;
  font-weight: 600;
}
.auth-divider {
  display: flex;
  align-items: center;
  gap: .75rem;
  color: var(--bs-secondary-color);
  font-size: .875rem;
}
.auth-divider::before,
.auth-divider::after {
  content: "";
  flex: 1;
  border-top: 1px solid var(--bs-border-color);
}
.btn-token-toggle .bi-chevron-down { transition: transform .2s ease-in-out; }
.btn-token-toggle[aria-expanded="true"] .bi-chevron-down { transform: rotate(180deg); }
</style>
