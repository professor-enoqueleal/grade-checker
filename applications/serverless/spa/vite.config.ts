/// <reference types="vitest" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { mockApi } from './mock/plugin';

/**
 * - `npm run dev`: /api goes to the backend of `sam local start-api` (VITE_API_TARGET, default http://127.0.0.1:3000),
 *   so the browser sees one origin, as it will behind CloudFront (no CORS, session cookie works).
 * - `npm run dev:mock`: /api is answered in memory by mock/ (no backend, no GitHub), to work on the screens.
 * - `npm run preview`: serves the production build (dist/) on http://localhost:4173, /api as in `npm run dev`.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const useMock = mode === 'mock';
  const apiProxy = { '/api': { target: env.VITE_API_TARGET || 'http://127.0.0.1:3000', changeOrigin: false } };
  return {
    plugins: [vue(), ...(useMock ? [mockApi()] : [])],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: useMock ? undefined : apiProxy,
    },
    // npm run preview: the production build (dist/), with /api to the same backend as npm run dev
    preview: {
      port: 4173,
      strictPort: true,
      proxy: apiProxy,
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 600,
    },
    test: {
      environment: 'jsdom',
      include: ['tests/**/*.spec.ts'],
      setupFiles: ['tests/setup.ts'],
    },
  };
});
