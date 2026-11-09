import { test, expect } from '@playwright/test';

/** openspec capability token-sign-in (change login-token-validacao). */
test.describe('Sign in with personal access token', () => {

  for (const [label, form] of [['only spaces', { 'access-token': '   ' }], ['missing parameter', {}]] as const) {
    test(`${label}: 400 with a message, never a server error`, async ({ request }) => {
      const response = await request.post('/login', { form });

      expect(response.status()).toBe(400);
      const html = await response.text();
      expect(html).toContain('Please enter a personal access token.');
      expect(html).toMatch(/class="collapse show" id="token-form"/);
    });
  }

  test('valid token without a previous session signs in (no server error)', async ({ playwright, baseURL }) => {
    test.skip(!process.env.E2E_GITHUB_TOKEN, 'E2E_GITHUB_TOKEN is not set');
    const fresh = await playwright.request.newContext({ baseURL }); // no cookies at all
    const response = await fresh.post('/login', { form: { 'access-token': process.env.E2E_GITHUB_TOKEN! }, maxRedirects: 0 });

    expect(response.status()).toBe(302);
    expect(response.headers()['location']).toMatch(/\/home$/);
    await fresh.dispose();
  });

  test('token rejected by GitHub: 401 with a message and no session (openspec change identidade-administrador)', async ({ playwright, baseURL }) => {
    const fresh = await playwright.request.newContext({ baseURL });
    const response = await fresh.post('/login', { form: { 'access-token': 'ghp_invalid_e2e_token' }, maxRedirects: 0 });

    expect(response.status()).toBe(401);
    expect(await response.text()).toContain('GitHub rejected this personal access token.');
    const home = await fresh.get('/home', { maxRedirects: 0 });
    expect(await home.text()).toContain('Please sign in to continue.');
    await fresh.dispose();
  });

  test('the token form is reopened and focused after an invalid submission', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /personal access token/i }).click();
    await page.getByLabel('Personal access token').fill('   '); // passes the browser "required" check
    await page.getByRole('button', { name: 'Sign in with token' }).click();

    await expect(page.getByRole('alert')).toContainText('Please enter a personal access token.');
    await expect(page.getByRole('alert')).toHaveClass(/alert-danger/);
    await expect(page.getByLabel('Personal access token')).toBeVisible();
    await expect(page.getByLabel('Personal access token')).toBeFocused();
  });
});
