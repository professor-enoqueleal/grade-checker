import { test as setup, expect, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { authFile, dataFile, excludedLogins, githubToken, testGroups } from '../support/env';

/** Signs in with a personal access token once and stores the session for the authenticated tests. */
setup('sign in with personal access token', async ({ page }) => {
  fs.mkdirSync(path.dirname(authFile), { recursive: true });

  if (!githubToken) {
    // keep an empty session so the authenticated project can start and skip its tests
    fs.writeFileSync(authFile, JSON.stringify({ cookies: [], origins: [] }));
    setup.skip(true, 'E2E_GITHUB_TOKEN is not set');
  }

  await page.goto('/login');
  await page.getByRole('button', { name: /personal access token/i }).click();
  await page.getByLabel('Personal access token').fill(githubToken);
  await page.getByRole('button', { name: 'Sign in with token' }).click();

  await expect(page).toHaveURL(/\/home$/);
  await page.context().storageState({ path: authFile });

  await prepareGroups(page);
});

/** Id of the group with this repository on /home, or undefined. */
async function findGroup(page: Page, name: string): Promise<string | undefined> {
  await page.goto('/home');
  const cards = await page.locator('.card').evaluateAll((els) => els.map((card) => ({
    title: (card.querySelector('.card-title')?.textContent ?? '').trim(),
    name: (card.querySelector('.list-group-item')?.textContent ?? '').replace('Name:', '').trim(),
  })));
  const card = cards.find((c) => c.name === name);
  return card ? card.title.replace('Group', '').trim() : undefined;
}

/**
 * As administrator, makes sure a test group is active (openspec capability group-management): creates it through the
 * API, or restores it when it was archived. Without administrator nothing can be created: the groups must exist.
 */
async function ensureGroup(page: Page, g: { name: string; url: string }) {
  const ajax = { 'X-Requested-With': 'XMLHttpRequest' };
  const created = await page.request.post('/groups/api', { form: { name: g.name, repositoryUrl: g.url }, headers: ajax });
  if (created.ok() || created.status() !== 409) {
    expect(created.ok(), `create group ${g.name}: ${created.status()}`).toBeTruthy();
    return;
  }
  // 409: already registered; when archived, restore it (the "Archived groups" table of /groups has its id)
  await page.goto('/groups');
  const archived = page.locator('#archived-groups-table tr', { hasText: g.url.replace('https://github.com/', '') });
  if (await archived.count() > 0) {
    const id = await archived.first().getAttribute('data-group-id');
    const restored = await page.request.post(`/groups/api/restore?id=${id}`, { headers: ajax });
    expect(restored.ok(), `restore group ${g.name}`).toBeTruthy();
  }
}

/**
 * db.sql may come without seed groups and groups start without members (openspec capability group-members):
 * creates the groups used by the tests when missing and, as administrator, marks their contributors as members.
 */
async function prepareGroups(page: Page) {
  const ids: Record<string, string> = {};

  for (const [key, g] of Object.entries(testGroups)) {
    let id = await findGroup(page, g.name);
    if (!id) {
      await ensureGroup(page, g);
      id = await findGroup(page, g.name);
    }
    expect(id, `group ${g.name}`).toBeTruthy();
    ids[key] = id!;

    await page.goto(`/detail?id=${id}`);
    const admin = (await page.locator('#content').getAttribute('data-admin')) === 'true';
    if (!admin) continue; // only administrators mark members: tests that need students skip themselves

    const logins = await page.locator('.mark-member-link').evaluateAll((links) => links.map((l) => l.getAttribute('data-login') ?? ''));
    for (const login of logins) {
      if (!login || login.endsWith('[bot]') || excludedLogins.includes(login.toLowerCase())) continue;
      const response = await page.request.post('/group-members', {
        form: { groupId: id!, login, manual: 'false' },
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
      expect([200, 409], `mark ${login} as member of ${g.name}`).toContain(response.status());
    }
  }

  fs.writeFileSync(dataFile, JSON.stringify(ids));
}
