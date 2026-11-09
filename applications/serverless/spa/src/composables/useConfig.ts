import { shallowRef } from 'vue';
import { api } from '@/api';
import type { AppConfig } from '@/api/types';

/** GET /api/config, read once per page load (periods depend on the day; a reload refreshes them). */
const config = shallowRef<AppConfig | null>(null);
let pending: Promise<AppConfig> | null = null;

export function useConfig() {
  async function load(): Promise<AppConfig> {
    if (config.value) return config.value;
    if (!pending) {
      pending = api.config()
        .then((c) => { config.value = c; return c; })
        .finally(() => { pending = null; });
    }
    return pending;
  }
  return { config, load };
}
