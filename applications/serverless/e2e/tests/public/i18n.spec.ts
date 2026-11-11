import { test, expect } from '@playwright/test';

/**
 * Capability serverless-i18n on the sign-in page: initial language from the saved choice, then the browser, then
 * Portuguese; the selector changes it at once and the choice survives a reload.
 */
test.describe('Language', () => {

  test.describe('browser in Portuguese', () => {
    test.use({ locale: 'pt-BR' });

    test('opens in Portuguese', async ({ page }) => {
      await page.goto('/login');
      await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
      await expect(page.getByRole('heading', { name: 'Entrar no Grade Checker' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Continuar com o GitHub' })).toBeVisible();
      await expect(page).toHaveTitle('Entrar · Grade Checker');
      await expect(page.locator('#language-pt-BR')).toHaveAttribute('aria-pressed', 'true');
      await expect(page.locator('#language-pt-BR')).toHaveAccessibleName('Português');
      await expect(page.locator('#language-en')).toHaveAttribute('aria-pressed', 'false');
      await expect(page.locator('#language-en')).toHaveAccessibleName('English');
      await expect(page.locator('#language-selector svg')).toHaveCount(2);   // the two flags, drawn by the SPA
    });

    test('English in the selector: at once, kept after a reload, Portuguese again without losing the form', async ({ page }) => {
      await page.goto('/login');
      await page.locator('#language-en').click();
      await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
      await expect(page).toHaveTitle('Sign in · Grade Checker');
      await expect(page.locator('html')).toHaveAttribute('lang', 'en');

      await page.reload();
      await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
      await expect(page.locator('#language-en')).toHaveAttribute('aria-pressed', 'true');

      // what was typed stays
      await page.getByRole('button', { name: /personal access token/i }).click();
      await page.locator('#access-token').fill('ghp_typed');
      await page.locator('#language-pt-BR').click();
      await expect(page.getByRole('heading', { name: 'Entrar no Grade Checker' })).toBeVisible();
      await expect(page.locator('#access-token')).toHaveValue('ghp_typed');
      await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
    });

    test('messages of the sign-in page in Portuguese', async ({ page }) => {
      await page.goto('/login?error=github_failed');
      await expect(page.getByRole('alert')).toContainText('O login com o GitHub falhou. Tente de novo.');
      await page.goto('/home');
      await expect(page.getByRole('alert')).toContainText('Entre para continuar.');
    });
  });

  test.describe('browser in English', () => {
    test.use({ locale: 'en-US' });

    test('opens in English', async ({ page }) => {
      await page.goto('/login');
      await expect(page.locator('html')).toHaveAttribute('lang', 'en');
      await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
      await expect(page.locator('#language-en')).toHaveAttribute('aria-pressed', 'true');
    });

    test('the saved choice wins over the browser', async ({ page }) => {
      await page.addInitScript(() => localStorage.setItem('locale', 'pt-BR'));
      await page.goto('/login');
      await expect(page.getByRole('heading', { name: 'Entrar no Grade Checker' })).toBeVisible();
    });
  });

  test.describe('browser in another language', () => {
    test.use({ locale: 'es-ES' });

    test('opens in Portuguese', async ({ page }) => {
      await page.goto('/login');
      await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
      await expect(page.getByRole('heading', { name: 'Entrar no Grade Checker' })).toBeVisible();
    });
  });
});
