import { reactive, readonly } from 'vue';
import { api } from '@/api';
import { ApiError } from '@/api/client';
import type { Me } from '@/api/types';

/**
 * Who is signed in (openspec capability platform-admin): login and administrator flag, read once from GET /api/me.
 * The flag only shows or hides links; every administrator call is authorized again by the backend.
 */
const state = reactive<{ me: Me | null; loaded: boolean }>({ me: null, loaded: false });
let pending: Promise<Me | null> | null = null;

async function load(force = false): Promise<Me | null> {
  if (state.loaded && !force) return state.me;
  if (pending && !force) return pending;
  pending = api.me()
    .then((me) => me)
    .catch((error) => {
      if (error instanceof ApiError && error.status === 401) return null;
      throw error;
    })
    .then((me) => {
      state.me = me;
      state.loaded = true;
      return me;
    })
    .finally(() => { pending = null; });
  return pending;
}

function signedIn(me: Me) {
  state.me = me;
  state.loaded = true;
}

function clear() {
  state.me = null;
  state.loaded = true;
}

export function useSession() {
  return { session: readonly(state), load, signedIn, clear };
}
