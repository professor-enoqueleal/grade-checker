import { test, expect } from '@playwright/test';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';
import { openUserMenu } from '../../support/navbar';

/** Capability serverless-i18n on the authenticated pages: navbar and group detail in Portuguese, selector in the navbar. */
test.describe('Language (authenticated)', () => {

  test.skip(!githubToken, skipWithoutToken);
  test.use({ locale: 'pt-BR' });

  test('navbar and group detail in Portuguese', async ({ page }) => {
    await page.goto(`/detail?id=${groupId}`);
    const navbar = page.locator('#main-navbar');
    await expect(navbar).toHaveAttribute('aria-label', 'Navegação principal');
    await expect(navbar.locator('#language-pt-BR')).toHaveAttribute('aria-pressed', 'true');
    const admin = (await page.locator('#content').getAttribute('data-admin')) === 'true';
    await openUserMenu(page);
    await expect(navbar.locator('#user-menu-signed-in')).toContainText('Conectado como');
    await expect(navbar.locator('#user-menu-role')).toHaveText(admin ? 'Administrador' : 'Aluno');
    await expect(navbar.locator('#sign-out-link')).toHaveText('Sair');
    await page.keyboard.press('Escape');
    if (admin) {
      await expect(navbar.locator('#manage-groups-link')).toHaveText('Gerenciar grupos');
      await expect(navbar.locator('#class-grades-link')).toHaveText('Notas da turma');
    }
    await expect(page).toHaveTitle('Grade Checker | Detalhes do grupo');
    await expect(page.getByRole('heading', { name: 'Informações do grupo' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Contribuidores e membros' })).toBeVisible();
    await expect(page.locator('#dashboard-title')).toHaveText('Painel de contribuições');
    await expect(page.locator('#dashboard-summary')).toContainText('(commits de merge excluídos)');
    await expect(page.locator('.contributions-table th').first()).toHaveText('Autor');

    // milestones and periods in Portuguese, from the raw values of the API
    const buttons = page.locator('#period-buttons');
    for (const name of ['Marco 1', 'Marco 2', 'Marco 3', 'Tudo']) await expect(buttons.getByText(name, { exact: true })).toBeVisible();
    await expect(page.locator('#dashboard-summary')).toContainText('Todo o histórico');
    await expect(page.locator('#dashboard-summary')).not.toContainText('All history');
  });

  test('commit history in Portuguese: dates and frequency chart', async ({ page }) => {
    await page.goto(`/detail?id=${groupId}`);
    const author = (await page.locator('.card[data-login]').first().getAttribute('data-login'))!;
    await page.goto(`/contributions-detail?${new URLSearchParams({ groupId, author })}`);
    await expect(page.locator('#period-text')).toHaveText('Todo o histórico (commits de merge excluídos)');
    const firstDate = page.locator('.commit-date').first();
    if (await firstDate.count()) {
      await expect(firstDate).toHaveText(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);
      await expect(page.locator('#commit-activity-title')).toHaveText(/^Commits por (dia|semana)$/);
      await expect(page.locator('#commit-activity-summary')).toContainText(/dias? ativos?/);
    }
  });

  test('the navbar selector changes the page at once and keeps the choice', async ({ page }) => {
    await page.goto(`/detail?id=${groupId}`);
    await expect(page.locator('#dashboard-title')).toHaveText('Painel de contribuições');
    await page.locator('#main-navbar #language-en').click();
    await expect(page.locator('#dashboard-title')).toHaveText('Contributions Dashboard');
    await expect(page.getByRole('heading', { name: 'Group information' })).toBeVisible();
    await expect(page).toHaveTitle('Grade Checker | Group Detail');

    await page.goto('/home');
    await expect(page.getByRole('heading', { name: 'Groups' })).toBeVisible();
  });
});
