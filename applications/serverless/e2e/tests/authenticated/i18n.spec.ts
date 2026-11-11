import { test, expect } from '@playwright/test';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** Capability serverless-i18n on the authenticated pages: navbar and group detail in Portuguese, selector in the navbar. */
test.describe('Language (authenticated)', () => {

  test.skip(!githubToken, skipWithoutToken);
  test.use({ locale: 'pt-BR' });

  test('navbar and group detail in Portuguese', async ({ page }) => {
    await page.goto(`/detail?id=${groupId}`);
    const navbar = page.locator('#main-navbar');
    await expect(navbar).toHaveAttribute('aria-label', 'Navegação principal');
    await expect(navbar.locator('#sign-out-link')).toHaveText('Sair');
    await expect(navbar.locator('#language-selector')).toHaveValue('pt-BR');
    if ((await page.locator('#content').getAttribute('data-admin')) === 'true') {
      await expect(navbar.locator('#manage-groups-link')).toHaveText('Gerenciar grupos');
      await expect(navbar.locator('#class-grades-link')).toHaveText('Notas da turma');
    }
    await expect(page).toHaveTitle('Grade Checker | Detalhes do grupo');
    await expect(page.getByRole('heading', { name: 'Informações do grupo' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Contribuidores e membros' })).toBeVisible();
    await expect(page.locator('#dashboard-title')).toHaveText('Painel de contribuições');
    await expect(page.locator('#dashboard-summary')).toContainText('(commits de merge excluídos)');
    await expect(page.locator('.contributions-table th').first()).toHaveText('Autor');
  });

  test('the navbar selector changes the page at once and keeps the choice', async ({ page }) => {
    await page.goto(`/detail?id=${groupId}`);
    await expect(page.locator('#dashboard-title')).toHaveText('Painel de contribuições');
    await page.locator('#main-navbar #language-selector').selectOption('en');
    await expect(page.locator('#dashboard-title')).toHaveText('Contributions Dashboard');
    await expect(page.getByRole('heading', { name: 'Group information' })).toBeVisible();
    await expect(page).toHaveTitle('Grade Checker | Group Detail');

    await page.goto('/home');
    await expect(page.getByRole('heading', { name: 'Groups' })).toBeVisible();
  });
});
