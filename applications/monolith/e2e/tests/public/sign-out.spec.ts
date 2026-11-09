import { test, expect } from '@playwright/test';
import { githubToken, skipWithoutToken } from '../../support/env';

/** openspec capability sign-out (change limpeza-debitos-tecnicos). Uses its own session, never the shared one. */
test.describe('Sign out', () => {

  test('logout shows a success message, not an error', async ({ page }) => {
    test.skip(!githubToken, skipWithoutToken);

    await page.goto('/login');
    await page.getByRole('button', { name: /personal access token/i }).click();
    await page.getByLabel('Personal access token').fill(githubToken);
    await page.getByRole('button', { name: 'Sign in with token' }).click();
    await expect(page).toHaveURL(/\/home$/);

    await page.goto('/logout');

    const alert = page.getByRole('alert');
    await expect(alert).toContainText('You have been signed out.');
    await expect(alert).toHaveClass(/alert-success/);

    // the session is really gone
    await page.goto('/home');
    await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
  });

  test('"Sign out" in the navbar signs out (openspec capability navigation-bar)', async ({ page }) => {
    test.skip(!githubToken, skipWithoutToken);

    await page.goto('/login');
    await page.getByRole('button', { name: /personal access token/i }).click();
    await page.getByLabel('Personal access token').fill(githubToken);
    await page.getByRole('button', { name: 'Sign in with token' }).click();
    await expect(page).toHaveURL(/\/home$/);

    await page.locator('#main-navbar #sign-out-link').click();
    await expect(page.getByRole('alert')).toContainText('You have been signed out.');
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
