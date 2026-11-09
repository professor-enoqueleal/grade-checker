import { test, expect } from '@playwright/test';
import { githubClientId } from '../../support/env';

/** openspec capability github-sign-in (change login-github-configuravel). */
test.describe('GitHub sign-in', () => {

  test('login page has no OAuth configuration; the button starts at /login/github', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByRole('link', { name: 'Continue with GitHub' })).toHaveAttribute('href', '/login/github');
    expect(await page.content()).not.toContain('client_id');
  });

  test('/login/github redirects to GitHub with the configured client id and a random state', async ({ request }) => {
    const first = await request.get('/login/github', { maxRedirects: 0 });
    const second = await request.get('/login/github', { maxRedirects: 0 });

    expect(first.status()).toBe(302);
    const location = new URL(first.headers()['location']);
    expect(location.origin + location.pathname).toBe('https://github.com/login/oauth/authorize');
    expect(location.searchParams.get('client_id')).toBe(githubClientId);
    expect(location.searchParams.get('state')).toMatch(/^[A-Za-z0-9_-]{43}$/);

    const otherState = new URL(second.headers()['location']).searchParams.get('state');
    expect(otherState).not.toBe(location.searchParams.get('state'));
  });

  test('forged callback (no matching state) is rejected', async ({ page }) => {
    await page.goto('/callback?code=forged-code&state=forged-state');

    await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
    await expect(page.getByRole('alert')).toContainText('GitHub sign-in could not be verified. Please try again.');
  });

  test('error returned by GitHub is shown', async ({ page }) => {
    await page.goto('/callback?error=access_denied&error_description=The+user+has+denied+your+application+access.');

    await expect(page.getByRole('alert')).toContainText('The user has denied your application access.');
  });
});
