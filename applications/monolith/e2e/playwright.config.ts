import { defineConfig } from '@playwright/test';
import path from 'path';

/**
 * Grade Checker E2E suite.
 *
 * By default the suite starts its own instance with `mvn jetty:run` on port 8099 and an
 * isolated H2 database under e2e/.tmp, so it never touches the developer database (~/h2db).
 * Set E2E_BASE_URL to run against an instance that is already running instead.
 *
 * Authenticated tests need a GitHub token in E2E_GITHUB_TOKEN (`npm run test:local` uses `gh auth token`).
 */
const port = process.env.E2E_PORT ?? '8099';
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${port}`;
const startServer = !process.env.E2E_BASE_URL;
const isolatedDb = path.resolve(__dirname, '.tmp/h2/grade-checker');
/** client id given to the instance started by the suite (checked by public/github-sign-in.spec.ts) */
export const githubClientId = process.env.E2E_GITHUB_CLIENT_ID || 'Iv1.e2e-test-client';

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  timeout: 90_000,
  expect: { timeout: 30_000 }, // GitHub GraphQL takes a few seconds per page on the first (uncached) call
  fullyParallel: false,
  workers: 1, // shared H2 database and GitHub rate limit
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  // stops the instance started by the suite with SIGTERM before Playwright kills it (SIGKILL): Jetty then stops the
  // web application and the database is closed, so the last writes are kept (openspec change fechar-banco-ao-desligar)
  globalTeardown: startServer ? path.resolve(__dirname, 'support/stop-server.ts') : undefined,

  use: {
    baseURL,
    // system Chrome by default (no browser download); override with E2E_BROWSER_CHANNEL=chromium
    channel: process.env.E2E_BROWSER_CHANNEL ?? 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off', // video needs a separate ffmpeg download; traces + screenshots are enough
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

  webServer: startServer
    ? {
        command: `mvn -q jetty:run -Djetty.http.port=${port}`,
        cwd: path.resolve(__dirname, '..'),
        url: `${baseURL}/login`,
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
        stdout: 'ignore',
        stderr: 'pipe',
        env: {
          ...(process.env as Record<string, string>),
          DB_URL: `jdbc:h2:${isolatedDb};DB_CLOSE_ON_EXIT=FALSE`,
          GITHUB_CLIENT_ID: githubClientId, // /login/github builds the authorize URL from it
          // administrator of the instance started by the suite (test:local sets it to the `gh` user)
          GRADE_CHECKER_ADMINS: process.env.E2E_ADMIN_LOGIN ?? '',
        },
      }
    : undefined,
});
