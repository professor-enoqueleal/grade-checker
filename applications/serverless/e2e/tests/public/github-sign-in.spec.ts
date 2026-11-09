import { test, expect } from '@playwright/test';
import { githubClientId } from '../../support/env';

/** Capabilities sign-in-page and session-auth (OAuth of the GitHub App with state). */
test.describe('GitHub sign-in', () => {

  test('login page has no OAuth configuration; the button starts at /api/auth/github', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByRole('link', { name: 'Continue with GitHub' })).toHaveAttribute('href', '/api/auth/github');
    expect(await page.content()).not.toContain('client_id');
  });

  test('/api/auth/github redirects to GitHub with the configured client id and a random state in a cookie', async ({ request }) => {
    const first = await request.get('/api/auth/github', { maxRedirects: 0 });
    const second = await request.get('/api/auth/github', { maxRedirects: 0 });

    expect(first.status()).toBe(302);
    const location = new URL(first.headers()['location']);
    expect(location.origin + location.pathname).toBe('https://github.com/login/oauth/authorize');
    expect(location.searchParams.get('client_id')).toBe(githubClientId);
    const state = location.searchParams.get('state');
    expect(state).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(first.headers()['set-cookie']).toContain(`__Host-gc_oauth=${state}; Path=/; Max-Age=600; HttpOnly; Secure; SameSite=Lax`);

    const otherState = new URL(second.headers()['location']).searchParams.get('state');
    expect(otherState).not.toBe(state);
  });

  test('forged callback (no matching state) is rejected', async ({ page }) => {
    await page.goto('/api/auth/callback?code=forged-code&state=forged-state');

    await expect(page).toHaveURL(/\/login\?error=github_not_verified$/);
    await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
    await expect(page.getByRole('alert')).toContainText('GitHub sign-in could not be verified. Please try again.');
  });

  test('error returned by GitHub shows a generic failure (the text of the URL is never shown)', async ({ page }) => {
    await page.goto('/api/auth/callback?error=access_denied&error_description=The+user+has+denied+your+application+access.');

    await expect(page.getByRole('alert')).toContainText('GitHub sign-in failed. Please try again.');
    await expect(page.locator('body')).not.toContainText('denied your application');
  });
});
