import { ref } from 'vue';

/**
 * Light / Dark / Auto toggle of the sign-in page (Bootstrap color modes), stored in localStorage as before.
 * As in the JSP version, the other pages stay light: their charts use light colors (see router afterEach).
 */
export type Theme = 'light' | 'dark' | 'auto';

const KEY = 'theme';
const current = ref<Theme>(stored());

function stored(): Theme {
  try {
    const value = localStorage.getItem(KEY);
    if (value === 'light' || value === 'dark' || value === 'auto') return value;
  } catch { /* storage blocked */ }
  return 'auto';
}

function prefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export function applyTheme(theme: Theme | null) {
  const root = document.documentElement;
  if (theme === null) {
    root.setAttribute('data-bs-theme', 'light');
    return;
  }
  root.setAttribute('data-bs-theme', theme === 'auto' ? (prefersDark() ? 'dark' : 'light') : theme);
}

export function useTheme() {
  function set(theme: Theme) {
    current.value = theme;
    try { localStorage.setItem(KEY, theme); } catch { /* storage blocked */ }
    applyTheme(theme);
  }
  return { theme: current, set };
}
