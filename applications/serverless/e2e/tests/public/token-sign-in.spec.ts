import { test, expect } from '@playwright/test';
import { ajax } from '../../support/env';

/** Capabilities sign-in-page and session-auth (POST /api/auth/token). */
test.describe('Sign in with personal access token', () => {

  for (const [label, data] of [['only spaces', { token: '   ' }], ['missing token', {}]] as const) {
    test(`${label}: 400 with a message, never a server error`, async ({ request }) => {
      const response = await request.post('/api/auth/token', { data, headers: ajax });

      expect(response.status()).toBe(400);
      expect(await response.json()).toEqual({ error: 'bad_request', message: 'Please enter a personal access token.' });
    });
  }

  test('valid token without a previous session signs in and sets only an HttpOnly session cookie', async ({ playwright, baseURL }) => {
    test.skip(!process.env.E2E_GITHUB_TOKEN, 'E2E_GITHUB_TOKEN is not set');
    const fresh = await playwright.request.newContext({ baseURL }); // no cookies at all
    const response = await fresh.post('/api/auth/token', { data: { token: process.env.E2E_GITHUB_TOKEN! }, headers: ajax });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Object.keys(body).sort()).toEqual(['isAdmin', 'login']);             // the token never goes back
    const cookie = response.headers()['set-cookie'];
    expect(cookie).toMatch(/^__Host-gc_session=[A-Za-z0-9_-]{43}; Path=\/; Max-Age=28800; HttpOnly; Secure; SameSite=Lax/);
    expect((await fresh.get('/api/me')).status()).toBe(200);
    await fresh.dispose();
  });

  test('token rejected by GitHub: 401 with a message and no session', async ({ playwright, baseURL }) => {
    const fresh = await playwright.request.newContext({ baseURL });
    const response = await fresh.post('/api/auth/token', { data: { token: 'ghp_invalid_e2e_token' }, headers: ajax });

    expect(response.status()).toBe(401);
    expect((await response.json()).message).toBe('GitHub rejected this personal access token.');
    expect(response.headers()['set-cookie']).toBeUndefined();
    expect((await fresh.get('/api/me')).status()).toBe(401);
    await fresh.dispose();
  });

  test('without X-Requested-With the sign-in is refused (CSRF)', async ({ request }) => {
    const response = await request.post('/api/auth/token', { data: { token: 'x' } });
    expect(response.status()).toBe(403);
    expect((await response.json()).message).toBe('Missing X-Requested-With header.');
  });

  test('the token form stays open and focused after an invalid submission', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /personal access token/i }).click();
    await page.getByLabel('Personal access token').fill('   '); // passes the browser "required" check
    await page.getByRole('button', { name: 'Sign in with token' }).click();

    await expect(page.getByRole('alert')).toContainText('Please enter a personal access token.');
    await expect(page.getByRole('alert')).toHaveClass(/alert-danger/);
    await expect(page.getByLabel('Personal access token')).toBeVisible();
    await expect(page.getByLabel('Personal access token')).toBeFocused();

    // rejected by GitHub: message, empty field, still focused
    await page.getByLabel('Personal access token').fill('ghp_invalid_e2e_token');
    await page.getByRole('button', { name: 'Sign in with token' }).click();
    await expect(page.getByRole('alert')).toContainText('GitHub rejected this personal access token.');
    await expect(page.getByLabel('Personal access token')).toHaveValue('');
    await expect(page.getByLabel('Personal access token')).toBeFocused();
  });
});
