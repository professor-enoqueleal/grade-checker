import { test, expect } from '@playwright/test';
import { ajax, githubToken, skipWithoutToken } from '../../support/env';

/** Capabilities navigation-bar (Sign out) and session-auth (logout). Uses its own session, never the shared one. */
test.describe('Sign out', () => {

  async function signIn(page: import('@playwright/test').Page) {
    await page.goto('/login');
    await page.getByRole('button', { name: /personal access token/i }).click();
    await page.getByLabel('Personal access token').fill(githubToken);
    await page.getByRole('button', { name: 'Sign in with token' }).click();
    await expect(page).toHaveURL(/\/home$/);
  }

  test('POST /api/auth/logout ends the session', async ({ page }) => {
    test.skip(!githubToken, skipWithoutToken);
    await signIn(page);

    const response = await page.request.post('/api/auth/logout', { headers: ajax });
    expect(response.status()).toBe(200);
    expect((await page.request.get('/api/me')).status()).toBe(401);

    // the session is really gone
    await page.goto('/home');
    await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
  });

  test('"Sign out" in the navbar signs out with a success message (capability navigation-bar)', async ({ page }) => {
    test.skip(!githubToken, skipWithoutToken);
    await signIn(page);

    await page.locator('#main-navbar #sign-out-link').click();
    const alert = page.getByRole('alert');
    await expect(alert).toContainText('You have been signed out.');
    await expect(alert).toHaveClass(/alert-success/);
    await expect(page.locator('#main-navbar')).toHaveCount(0);   // the login page has no navbar

    await page.goto('/home');
    await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
  });

  test('error messages are still shown as errors', async ({ page }) => {
    await page.goto('/home');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText('Please sign in to continue.');
    await expect(alert).toHaveClass(/alert-danger/);
  });
});
