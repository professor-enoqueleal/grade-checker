<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '@/api';
import { ApiError } from '@/api/client';
import { useSession } from '@/composables/useSession';
import ThemeToggle from '@/components/ThemeToggle.vue';

/**
 * Sign-in page (capabilities github-sign-in, token-sign-in, sign-out). "Continue with GitHub" is a full navigation to
 * /api/auth/github; the backend comes back here with ?error=… when the GitHub sign-in fails, or after signing out.
 */
const route = useRoute();
const router = useRouter();
const { signedIn } = useSession();

const CALLBACK_ERRORS: Record<string, string> = {
  github_not_verified: 'GitHub sign-in could not be verified. Please try again.',
  github_failed: 'GitHub sign-in failed. Please try again.',
};

const message = ref<{ type: 'success' | 'danger'; text: string } | null>(
  route.query.signedOut ? { type: 'success', text: 'You have been signed out.' }
    : route.query.signIn === 'required' ? { type: 'danger', text: 'Please sign in to continue.' }
    : typeof route.query.error === 'string' ? { type: 'danger', text: CALLBACK_ERRORS[route.query.error] ?? CALLBACK_ERRORS.github_failed }
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
    message.value = { type: 'danger', text: 'Please enter a personal access token.' };
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
      text: e instanceof ApiError && e.status === 401 ? 'GitHub rejected this personal access token.'
        : 'Could not verify the token with GitHub. Please try again.',
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
    <ThemeToggle />

    <main class="auth-wrapper">
      <div class="auth-card">

        <div class="text-center mb-4">
          <span class="auth-logo mb-3" aria-hidden="true"><i class="bi bi-mortarboard-fill"></i></span>
          <h1 class="h4 fw-semibold mb-1">Sign in to Grade Checker</h1>
          <p class="text-body-secondary small mb-0">Use your GitHub account to continue</p>
        </div>

        <div v-if="message" class="alert alert-dismissible fade show small" :class="`alert-${message.type}`" role="alert">
          <i class="bi me-1" :class="message.type === 'success' ? 'bi-check-circle' : 'bi-exclamation-circle'"></i>
          {{ message.text }}
          <button type="button" class="btn-close" aria-label="Close" @click="message = null"></button>
        </div>

        <div class="card border-0 shadow-sm rounded-3">
          <div class="card-body p-4">

            <a class="btn btn-github btn-lg w-100 d-flex align-items-center justify-content-center gap-2 fs-6 py-2"
               :href="api.gitHubSignInUrl">
              <i class="bi bi-github" aria-hidden="true"></i>
              Continue with GitHub
            </a>

            <div class="auth-divider my-4">or</div>

            <button class="btn btn-link btn-token-toggle w-100 text-body-secondary text-decoration-none small d-flex align-items-center justify-content-center gap-1 p-0"
                    type="button" :aria-expanded="tokenOpen" aria-controls="token-form" @click="toggleToken">
              <i class="bi bi-key"></i>
              Sign in with a personal access token
              <i class="bi bi-chevron-down small"></i>
            </button>

            <div v-show="tokenOpen" id="token-form">
              <form class="pt-3" @submit.prevent="signInWithToken">
                <label for="access-token" class="form-label small fw-semibold">Personal access token</label>
                <input ref="tokenInput" v-model="token" type="password" name="access-token" class="form-control" id="access-token"
                       placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" autocomplete="off" required>
                <div class="form-text">
                  Generate one in
                  <a href="https://github.com/settings/tokens" target="_blank" rel="noopener noreferrer">GitHub settings</a>.
                </div>
                <button type="submit" class="btn btn-outline-secondary w-100 mt-3" :disabled="sending">Sign in with token</button>
              </form>
            </div>

          </div>
        </div>

        <p class="text-center text-body-secondary small mt-4 mb-0">
          Grade Checker uses GitHub to read repositories, commits and contributors.
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
