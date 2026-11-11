import { defineConfig } from '@playwright/test';
import path from 'path';
import { baseURL, ownEnvironment } from './support/compose';

/**
 * Grade Checker E2E suite of the SERVERLESS version (the suite in applications/monolith/e2e tests the monolith).
 *
 * By default the suite starts its own Docker Compose project, grade-checker-e2e, on its own ports (SPA on 5183) with a
 * database that is created for the run and deleted at the end (support/compose.ts): the developer environment on
 * port 5173 is never touched. Set E2E_BASE_URL to run against an environment that is already running instead.
 *
 * Authenticated tests need a GitHub token in E2E_GITHUB_TOKEN (`npm run test:local` uses `gh auth token`).
 */
export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  timeout: 90_000,
  expect: { timeout: 30_000 }, // GitHub GraphQL takes a few seconds per page on the first (uncached) call
  fullyParallel: false,
  workers: 1, // shared database and GitHub rate limit
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  globalSetup: ownEnvironment ? path.resolve(__dirname, 'support/compose-up.ts') : undefined,
  globalTeardown: ownEnvironment ? path.resolve(__dirname, 'support/compose-down.ts') : undefined,

  use: {
    baseURL,
    // system Chrome by default (no browser download); override with E2E_BROWSER_CHANNEL=chromium
    channel: process.env.E2E_BROWSER_CHANNEL ?? 'chrome',
    // browser in English: the SPA opens in English (capability serverless-i18n) and the texts the tests check are the
    // English ones; tests/**/i18n.spec.ts set other languages
    locale: 'en-US',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    { name: 'public', testMatch: /public\/.*\.spec\.ts/ },
    {
      name: 'authenticated',
      testMatch: /authenticated\/.*\.spec\.ts/,
      dependencies: ['setup'],
      use: { storageState: path.resolve(__dirname, '.auth/user.json') },
    },
  ],
});
