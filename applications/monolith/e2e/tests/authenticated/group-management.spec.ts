import { test, expect, Page } from '@playwright/test';
import { githubToken, groupId, skipWithoutToken, testGroups } from '../../support/env';

/** openspec capability group-management (change gerenciamento-de-grupos). */
test.describe('Group management', () => {

  test.skip(!githubToken, skipWithoutToken);

  const ajax = { 'X-Requested-With': 'XMLHttpRequest' };
  // public repositories that never belong to a student group
  // stored = spelling of GitHub, shown in the table as "owner/repository" (the link keeps the full URL)
  const first = { name: 'e2e-group', url: 'https://github.com/octocat/hello-world.git', stored: 'octocat/Hello-World' };
  const second = { url: 'https://github.com/octocat/spoon-knife', stored: 'octocat/Spoon-Knife' };

  async function isAdmin(page: Page) {
    await page.goto(`/detail?id=${groupId}`);
    return (await page.locator('#content').getAttribute('data-admin')) === 'true';
  }

  /** Row of a group in /groups (active or archived table) by its repository ("owner/repository"). */
  function row(page: Page, table: 'groups-table' | 'archived-groups-table', url: string) {
    return page.locator(`#${table} tbody tr`, { hasText: url });
  }

  /** Id of the e2e group, active (created or restored here); the test archives it again at the end. */
  async function activeTestGroup(page: Page): Promise<string> {
    await page.goto('/groups');
    for (const url of [first.stored, second.stored]) {
      const archived = row(page, 'archived-groups-table', url);
      if (await archived.count() > 0) {
        const id = (await archived.first().getAttribute('data-group-id'))!;
        expect((await page.request.post(`/groups/api/restore?id=${id}`, { headers: ajax })).ok()).toBeTruthy();
      }
      await page.goto('/groups');
      const active = row(page, 'groups-table', url);
      if (await active.count() > 0) {
        const id = (await active.first().getAttribute('data-group-id'))!;
        // back to the initial state: first repository and name
        expect((await page.request.put('/groups/api', { params: { id, name: first.name, repositoryUrl: first.url }, headers: ajax })).ok()).toBeTruthy();
        return id;
      }
    }
    const created = await page.request.post('/groups/api', { form: { name: first.name, repositoryUrl: first.url }, headers: ajax });
    expect(created.ok(), `create: ${created.status()}`).toBeTruthy();
    return String((await created.json()).id);
  }

  test('"Manage groups" in the navbar for the administrator only', async ({ page }) => {
    const admin = await isAdmin(page);
    await page.goto('/home');
    const link = page.locator('#main-navbar #manage-groups-link');
    if (!admin) {
      await expect(page.locator('#manage-groups-link')).toHaveCount(0);
      await expect(page.locator('#add-group-home-link')).toHaveCount(0);
      return;
    }
    await expect(link).toHaveText('Manage groups');
    const grades = (await page.locator('#class-grades-link').boundingBox())!;
    expect((await link.boundingBox())!.x).toBeLessThan(grades.x);                       // before Class grades
    await link.click();
    await expect(page).toHaveURL(/\/groups$/);
    await expect(page.locator('#manage-groups-link')).toHaveAttribute('aria-current', 'page');
    await expect(page.locator('.breadcrumb-item')).toHaveText(['Home', 'Manage groups']);
  });

  test('page lists the active groups with Edit and Archive hyperlinks; ?add=1 opens the form', async ({ page }) => {
    test.skip(!(await isAdmin(page)), 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    await page.goto('/groups');
    await expect(page.locator('h4')).toHaveText('Manage groups');
    const clinica = row(page, 'groups-table', testGroups.groupId.url.replace('https://github.com/', ''));
    await expect(clinica).toHaveCount(1);
    await expect(clinica.locator('.group-actions a')).toHaveText(['Edit', 'Archive']);
    await expect(page.locator('#groups-table button')).toHaveCount(0);
    expect(Number(await clinica.locator('.group-members').innerText())).toBeGreaterThanOrEqual(0);

    // fits a phone: no horizontal scroll, Members hidden
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto('/groups');
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
    expect(await page.locator('#groups-table').evaluate((t) => t.parentElement!.scrollWidth > t.parentElement!.clientWidth)).toBe(false);
    await expect(page.locator('#groups-table th', { hasText: 'Members' })).toBeHidden();
    await page.setViewportSize({ width: 1280, height: 800 });

    await page.goto('/groups?add=1');
    await expect(page.locator('#groupModal')).toBeVisible();
    await expect(page.locator('#groupModalLabel')).toHaveText('Add a new group');
  });

  test('add, edit (members stay), archive and restore a group; errors in the form', async ({ page }) => {
    test.setTimeout(180_000);
    test.skip(!(await isAdmin(page)), 'the signed-in user is not in GRADE_CHECKER_ADMINS');
    const id = await activeTestGroup(page);

    try {
      // stored with the spelling of GitHub, owner and repository from the URL
      await page.goto('/groups');
      let line = page.locator(`#groups-table tr[data-group-id="${id}"]`);
      await expect(line.locator('.group-name')).toHaveText(first.name);
      await expect(line.locator('.repository-cell')).toHaveText(first.stored);
      await expect(line.locator('.repository-cell a')).toHaveAttribute('href', `https://github.com/${first.stored}`);

      // form errors: invalid URL, missing repository, repository of another group
      await page.locator('#add-group-link').click();
      await page.locator('#group-name').fill('x');
      for (const [url, message] of [
        ['github.com/clinica', 'Enter the URL of a GitHub repository (https://github.com/owner/repository).'],
        ['https://github.com/octocat/e2e-repository-that-does-not-exist-4242', 'Repository not found or not accessible with this token.'],
        [`${testGroups.groupId.url}/`, 'This repository is already registered.'],
      ]) {
        await page.locator('#repository-url').fill(url);
        await page.locator('#save-group').click();
        await expect(page.locator('#group-form-error'), url).toHaveText(message);
      }
      await page.locator('#groupModal .btn-close').click();

      // a member, to check that editing keeps it
      expect([200, 409]).toContain((await page.request.post('/group-members',
        { form: { groupId: id, login: 'octocat', manual: 'true' }, headers: ajax })).status());

      // edit name and URL: warning about contributors, grades and seeds; same id, the member stays
      await page.goto('/groups');
      line = page.locator(`#groups-table tr[data-group-id="${id}"]`);
      await line.locator('.edit-group-link').click();
      await expect(page.locator('#groupModalLabel')).toHaveText('Edit group');
      await expect(page.locator('#repository-change-warning')).toBeHidden();
      await page.locator('#group-name').fill('e2e-group-renamed');
      await page.locator('#repository-url').fill(second.url);
      await expect(page.locator('#repository-change-warning')).toBeVisible();
      await Promise.all([page.waitForURL(/\/groups$/), page.locator('#save-group').click()]);
      line = page.locator(`#groups-table tr[data-group-id="${id}"]`);
      await expect(line.locator('.group-name')).toHaveText('e2e-group-renamed');
      await expect(line.locator('.repository-cell')).toHaveText(second.stored);
      await expect(line.locator('.group-members')).toHaveText('1');

      // archive: out of the home and /grades, /detail 404, listed as archived
      page.once('dialog', (d) => d.accept());
      await Promise.all([page.waitForURL(/\/groups$/), line.locator('.archive-group-link').click()]);
      await expect(row(page, 'archived-groups-table', second.stored)).toHaveCount(1);
      await expect(row(page, 'groups-table', second.stored)).toHaveCount(0);
      await page.goto('/home');
      await expect(page.locator('.card .list-group-item', { hasText: 'e2e-group-renamed' })).toHaveCount(0);
      expect((await page.request.get(`/detail?id=${id}`)).status()).toBe(404);
      await page.goto('/grades');
      await expect(page.locator(`#grades-tbody tr[data-group-id="${id}"]`)).toHaveCount(0);

      // the repository of an archived group cannot be registered again
      const again = await page.request.post('/groups/api', { form: { name: 'again', repositoryUrl: second.url }, headers: ajax });
      expect(again.status()).toBe(409);
      expect((await again.json()).message).toBe('This repository is already registered in an archived group. Restore it instead.');

      // restore: back with the member
      await page.goto('/groups');
      await Promise.all([page.waitForURL(/\/groups$/), row(page, 'archived-groups-table', second.stored).locator('.restore-group-link').click()]);
      line = page.locator(`#groups-table tr[data-group-id="${id}"]`);
      await expect(line.locator('.group-members')).toHaveText('1');
      expect((await page.request.get(`/detail?id=${id}`)).status()).toBe(200);
      await page.goto('/home');
      await expect(page.locator('.card .list-group-item', { hasText: 'e2e-group-renamed' })).toHaveCount(1);
    } finally {
      // leave the e2e group archived, out of the home and of the class grades
      await page.request.delete(`/group-members?groupId=${id}&login=octocat`, { headers: ajax });
      await page.request.post(`/groups/api/archive?id=${id}`, { headers: ajax });
    }
  });

  test('API refuses calls without X-Requested-With; the old route no longer exists', async ({ page }) => {
    await page.goto('/home');
    expect((await page.request.post('/groups/api', { form: { name: 'x', repositoryUrl: second.url } })).status()).toBe(403);
    expect((await page.request.post('/group/create', { form: { 'group-name': 'x' } })).status()).toBe(404);
  });

  test('non-administrators get 403 from the page and the API', async ({ page }) => {
    test.skip(await isAdmin(page), 'the signed-in user is an administrator');

    const pageResponse = await page.goto('/groups');
    expect(pageResponse!.status()).toBe(403);
    await expect(page.locator('#groups-page-error')).toHaveText('Only administrators can manage groups.');
    const api = await page.request.post('/groups/api', { form: { name: 'x', repositoryUrl: second.url }, headers: ajax });
    expect(api.status()).toBe(403);
    expect((await api.json()).message).toBe('Only administrators can manage groups.');
  });
});
