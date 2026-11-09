import { test, expect } from '@playwright/test';

test.describe('Login page', () => {

  test('GitHub sign-in is the primary option and the token form is secondary', async ({ page }) => {
    await page.goto('/login');

    await expect(page).toHaveTitle('Sign in · Grade Checker'); // also guards UTF-8 rendering
    await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();

    const github = page.getByRole('link', { name: 'Continue with GitHub' });
    await expect(github).toBeVisible();
    await expect(github).toHaveAttribute('href', '/api/auth/github'); // OAuth URL is built by the backend

    const token = page.getByLabel('Personal access token');
    await expect(token).toBeHidden();

    await page.getByRole('button', { name: /personal access token/i }).click();
    await expect(token).toBeVisible();
    await expect(token).toBeFocused();
  });

  test('light and dark themes can be selected', async ({ page }) => {
    await page.goto('/login');

    await page.locator('#bd-theme').click();
    await page.locator('[data-bs-theme-value="dark"]').click();
    await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'dark');

    await page.locator('#bd-theme').click();
    await page.locator('[data-bs-theme-value="light"]').click();
    await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'light');
  });

  test('page is centered', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/login');

    const box = await page.locator('.auth-card').boundingBox();
    expect(box).not.toBeNull();
    const center = box!.x + box!.width / 2;
    expect(Math.abs(center - 640)).toBeLessThan(5);
  });
});
