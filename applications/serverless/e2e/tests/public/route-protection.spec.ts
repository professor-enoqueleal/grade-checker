import { test, expect } from '@playwright/test';

/** Capabilities spa-shell (route protection in the browser) and api-routing (401 of the API). */
test.describe('Route protection', () => {

  for (const route of ['/home', '/detail?id=1', '/groups', '/grades', '/contributions-detail?groupId=1&author=x', '/detail.jsp']) {
    test(`${route} requires sign in`, async ({ page }) => {
      await page.goto(route);

      await expect(page.getByRole('heading', { name: 'Sign in to Grade Checker' })).toBeVisible();
      await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
    });
  }

  test('API call without session gets 401 JSON', async ({ request }) => {
    for (const path of ['/api/me', '/api/groups', '/api/groups/1/contributions?period=all', '/api/grades']) {
      const response = await request.get(path, { headers: { Accept: 'application/json' } });
      expect(response.status(), path).toBe(401);
      expect(await response.json(), path).toEqual({ error: 'unauthorized', message: 'Sign in to continue.' });
    }
  });

  test('the cache administration API of the Jetty version does not exist', async ({ request }) => {
    const response = await request.get('/api/admin/cache/stats', { headers: { 'X-ADMIN-TOKEN': 'wrong' } });
    expect(response.status()).toBe(404);
    expect(await response.json()).toEqual({ error: 'not_found', message: 'Not found.' });
  });

  test('the files of the SPA are public; missing files are 404, not the page', async ({ request }) => {
    const index = await request.get('/');
    expect(index.status()).toBe(200);
    const html = await index.text();
    const assets = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((m) => m[1]);
    expect(assets.length).toBeGreaterThan(0);
    for (const asset of [...assets, '/img/group.png']) {
      expect((await request.get(asset)).status(), asset).toBe(200);
    }
    expect((await request.get('/assets/does-not-exist.js')).status()).toBe(404);
  });

  test('a path that only contains "/login" is still protected', async ({ page }) => {
    await page.goto('/groups/login');
    await expect(page.getByRole('alert')).toContainText('Please sign in to continue.');
  });
});
