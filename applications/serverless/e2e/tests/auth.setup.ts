import { test as setup, expect, Page } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { ajax, authFile, dataFile, excludedLogins, githubToken, testGroups } from '../support/env';

/** Signs in with a personal access token once and stores the session (cookie) for the authenticated tests. */
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

/** Id of the active group with this name, or undefined (GET /api/groups). */
async function findGroup(page: Page, name: string): Promise<string | undefined> {
  const groups = (await (await page.request.get('/api/groups')).json()) as Array<{ id: number; name: string }>;
  const group = groups.find((g) => g.name === name);
  return group ? String(group.id) : undefined;
}

/**
 * As administrator, makes sure a test group is active (capability groups-api): creates it, or restores it when it was
 * archived. Without administrator nothing can be created: the groups must exist (the seed creates them).
 */
async function ensureGroup(page: Page, g: { name: string; url: string }) {
  const created = await page.request.post('/api/groups', { data: { name: g.name, repositoryUrl: g.url }, headers: ajax });
  if (created.ok() || created.status() !== 409) {
    expect(created.ok(), `create group ${g.name}: ${created.status()}`).toBeTruthy();
    return;
  }
  // 409: already registered; when archived, restore it
  const archived = (await (await page.request.get('/api/groups/archived')).json()) as { groups: Array<{ id: number; repositoryUrl: string }> };
  const group = archived.groups.find((a) => a.repositoryUrl.toLowerCase() === g.url.toLowerCase());
  if (group) {
    const restored = await page.request.post(`/api/groups/${group.id}/restore`, { headers: ajax });
    expect(restored.ok(), `restore group ${g.name}`).toBeTruthy();
  }
}

/**
 * Groups start without members (capability members-api): makes sure the groups used by the tests exist and, as
 * administrator, marks their contributors as members.
 */
async function prepareGroups(page: Page) {
  const ids: Record<string, string> = {};
  const me = (await (await page.request.get('/api/me')).json()) as { isAdmin: boolean };

  for (const [key, g] of Object.entries(testGroups)) {
    let id = await findGroup(page, g.name);
    if (!id && me.isAdmin) {
      await ensureGroup(page, g);
      id = await findGroup(page, g.name);
    }
    expect(id, `group ${g.name}`).toBeTruthy();
    ids[key] = id!;
    if (!me.isAdmin) continue; // only administrators mark members: tests that need students skip themselves

    const detail = (await (await page.request.get(`/api/groups/${id}`)).json()) as { cards: Array<{ login: string; member: boolean }> };
    for (const card of detail.cards) {
      if (card.member || card.login.endsWith('[bot]') || excludedLogins.includes(card.login.toLowerCase())) continue;
      const response = await page.request.post(`/api/groups/${id}/members`, { data: { login: card.login, manual: false }, headers: ajax });
      expect([200, 409], `mark ${card.login} as member of ${g.name}`).toContain(response.status());
    }
  }

  fs.writeFileSync(dataFile, JSON.stringify(ids));
}
