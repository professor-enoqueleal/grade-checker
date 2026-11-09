import { test, expect } from '@playwright/test';

/** openspec/changes/proteger-rotas */
test.describe('Route protection', () => {

  for (const route of ['/home', '/detail?id=1', '/groups', '/contributions-detail', '/detail.jsp']) {
    test(`${route} requires sign in`, async ({ page }) => {
      await page.goto(route);

      await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
      await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
    });
  }

  test('AJAX call without session gets 401 JSON instead of the login page', async ({ request }) => {
    const response = await request.get('/contributors-stats?owner=o&repo=r', { headers: { Accept: 'application/json' } });

    expect(response.status()).toBe(401);
    expect(await response.json()).toMatchObject({ error: 'unauthorized' });
  });

  test('admin cache API is outside the login and uses its own token (both routes)', async ({ request }) => {
    const stats = await request.get('/admin/cache/stats', { headers: { 'X-ADMIN-TOKEN': 'wrong' } });
    expect(stats.status()).toBe(403);
    expect(await stats.json()).toEqual({ error: 'forbidden' });

    const action = await request.post('/admin/cache', { headers: { 'X-ADMIN-TOKEN': 'wrong' }, form: { action: 'invalidateAll' } });
    expect(action.status()).toBe(403);
    expect(await action.json()).toEqual({ error: 'forbidden' });
  });

  test('static assets of the login page are public', async ({ request }) => {
    for (const asset of ['/css/style.css', '/js/color-modes.js', '/webjars/bootstrap/5.3.0/css/bootstrap.min.css']) {
      expect((await request.get(asset)).status(), asset).toBe(200);
    }
  });

  test('a path that only contains "/login" is still protected', async ({ page }) => {
    await page.goto('/groups/login');
    await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
  });
});
