import { test, expect, Page } from '@playwright/test';
import { captureStats } from '../../support/dashboard';
import { aliasGroupId, githubToken, skipWithoutToken } from '../../support/env';

/** Capabilities group-detail-page (cards, Mark/Unmark), group-members-page and members-api. */
test.describe('Group members', () => {

  test.skip(!githubToken, skipWithoutToken);

  const ajax = { 'X-Requested-With': 'XMLHttpRequest' };
  const members = () => `/api/groups/${aliasGroupId}/members`;
  const aliases = () => `/api/groups/${aliasGroupId}/aliases`;
  const outsider = 'octocat'; // exists on GitHub, never commits in the students' repositories

  async function open(page: Page) {
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${aliasGroupId}`));
    const admin = (await page.locator('#content').getAttribute('data-admin')) === 'true';
    return { admin, body };
  }

  async function counters(page: Page) {
    return {
      contributors: Number(await page.locator('#number-of-contributors').inputValue()),
      members: Number(await page.locator('#members').inputValue()),
    };
  }

  /**
   * Runs an action that changes members and waits for the page to load its data again: the SPA reloads the group
   * (cards) or the member list, not the whole page as the JSP did.
   */
  async function reloading(page: Page, action: () => Promise<unknown>) {
    const reload = (url: string, method: string) => method === 'GET'
      && new RegExp(`/api/groups/${aliasGroupId}(/members)?$`).test(new URL(url).pathname);
    await Promise.all([page.waitForResponse((r) => reload(r.url(), r.request().method())), action()]);
  }

  async function removeQuietly(page: Page, login: string) {
    await page.request.delete(`${members()}/${login}`, { headers: ajax });
  }

  test('Contributors and members: counters, Member badge and card links (hyperlinks only)', async ({ page }) => {
    const { admin } = await open(page);

    await expect(page.locator('h4', { hasText: 'Contributors and members' })).toBeVisible();
    await expect(page.locator('h4', { hasText: /^Members$/ })).toHaveCount(0); // no separate members section

    const cards = page.locator('.card[data-login]');
    const all = await cards.evaluateAll((els) => els.map((el) => ({
      login: el.getAttribute('data-login') ?? '',
      contributions: (el.querySelector('.contributions-count')?.textContent ?? '').trim(),
      member: !!el.querySelector('.member-badge'),
    })));
    const c = await counters(page);
    const contributors = all.filter((x) => x.contributions !== 'Contributions: 0');
    expect(c.contributors).toBe(contributors.length);
    expect(c.members).toBe(all.filter((x) => x.member).length);
    // members without commits come after the contributors and are always members
    const firstZero = all.findIndex((x) => x.contributions === 'Contributions: 0');
    if (firstZero >= 0) expect(all.slice(firstZero).every((x) => x.member && x.contributions === 'Contributions: 0')).toBe(true);

    for (const card of await cards.all()) {
      const member = (await card.locator('.member-badge').count()) === 1;
      const footer = card.locator('.card-footer');
      await expect(footer.locator('button')).toHaveCount(0);
      const links = (await footer.locator('a.card-link').allInnerTexts()).map((t) => t.trim());
      expect(links.slice(0, 2)).toEqual(['GitHub', 'Details']);
      if (admin) {
        const action = footer.locator('a.card-link').nth(2);
        await expect(action).toHaveText(member ? 'Unmark' : 'Mark');
        await expect(action).toHaveAttribute('title', member ? 'Unmark as member' : 'Mark as member');
      } else {
        expect(links).toHaveLength(2);
      }
    }
  });

  test('administrator unmarks and marks a contributor from the card', async ({ page }) => {
    const { admin } = await open(page);
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS (set E2E_ADMIN_LOGIN / .env.local)');

    // a member with commits: unmarking keeps the card (a member without commits loses the card)
    const unmark = page.locator('.card[data-login]')
      .filter({ hasNot: page.getByText('Contributions: 0', { exact: true }) })
      .locator('.unmark-member-link').last();
    test.skip(await unmark.count() === 0, `group ${aliasGroupId} has no member with commits`);
    const login = (await unmark.getAttribute('data-login'))!;
    const before = await counters(page);

    try {
      page.once('dialog', (d) => d.accept());
      await reloading(page, () => unmark.click());
      await expect(page.locator(`.mark-member-link[data-login="${login}"]`)).toBeVisible();
      expect((await counters(page)).members).toBe(before.members - 1);
      await expect(page.locator(`.card[data-login="${login}"] .member-badge`)).toHaveCount(0);

      await reloading(page, () => page.locator(`.mark-member-link[data-login="${login}"]`).click());
      await expect(page.locator(`.unmark-member-link[data-login="${login}"]`)).toBeVisible();
      expect(await counters(page)).toEqual(before);
    } finally {
      await page.request.post(members(), { data: { login, manual: false }, headers: ajax });
    }
  });

  async function manage(page: Page) {
    await page.goto(`/group/members?id=${aliasGroupId}`);
  }

  function row(page: Page, login: string) {
    return page.locator(`#members-table tr[data-login="${login}"]`);
  }

  test('Manage members: link in the group page, breadcrumb and table with hyperlinks', async ({ page }) => {
    const { admin } = await open(page);
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    await expect(page.locator('#add-member-link')).toHaveCount(0);
    await expect(page.locator('#exampleModal')).toHaveCount(0);
    const members = (await counters(page)).members;
    await page.locator('#manage-members-link', { hasText: 'Manage members' }).click();
    await expect(page).toHaveURL(new RegExp(`/group/members\\?id=${aliasGroupId}$`));

    await expect(page.locator('.breadcrumb-item')).toHaveText(['Home', `Group ${aliasGroupId}`, 'Manage members']);
    expect(await page.locator('.breadcrumb-item a').evaluateAll((els) => els.map((el) => el.getAttribute('href'))))
      .toEqual(['/home', `/detail?id=${aliasGroupId}`]);
    await expect(page.locator('#add-member-link')).toHaveText('Add a new member');

    const rows = page.locator('#members-table tbody tr');
    await expect(rows).toHaveCount(members);
    if (members === 0) {
      await expect(page.locator('#no-members')).toHaveText('No members yet.');
    } else {
      const logins = await rows.evaluateAll((els) => els.map((el) => el.getAttribute('data-login') ?? ''));
      expect(logins).toEqual([...logins].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase())));
      await expect(rows.first().locator('.member-actions a')).toHaveText(['Edit', 'Remove']);
      await expect(page.locator('#members-table button')).toHaveCount(0);
    }
  });

  test('Add a new member in Manage members: student without commits counts only as member and appears with zeros', async ({ page }) => {
    const { admin } = await open(page);
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS');
    await removeQuietly(page, outsider);
    await open(page);
    const before = await counters(page);

    try {
      await manage(page);
      await page.locator('#add-member-link').click();
      await expect(page.locator('#memberModalLabel')).toHaveText('Add group member');
      await page.locator('#user-name').fill('Octo Cat');
      await page.locator('#github-username').fill(outsider);
      await reloading(page, () => page.locator('#save-member').click());
      await expect(row(page, outsider).locator('.member-name')).toHaveText('Octo Cat');

      // duplicated (ignoring case) and nonexistent logins are refused in the form
      await page.locator('#add-member-link').click();
      await page.locator('#user-name').fill('Again');
      await page.locator('#github-username').fill(outsider.toUpperCase());
      await page.locator('#save-member').click();
      await expect(page.locator('#member-form-error')).toHaveText('This user is already a member of the group.');
      await page.locator('#github-username').fill('e2e-user-that-does-not-exist-4242');
      await page.locator('#save-member').click();
      await expect(page.locator('#member-form-error')).toHaveText('This GitHub user does not exist.');
      await page.locator('#memberModal .btn-close').click();
      await expect(page.locator('#memberModal')).toBeHidden();

      // in the group page: a card with Contributions: 0 and the Member badge, after the contributors
      await open(page);
      const card = page.locator(`.card[data-login="${outsider}"]`);
      await expect(card.locator('.contributions-count')).toHaveText('Contributions: 0');
      await expect(card.locator('.member-badge')).toHaveText('Member');
      await expect(card).not.toContainText('no commits');
      await expect(card.locator('a.card-link')).toHaveText(['GitHub', 'Details', 'Unmark']);
      await expect(page.locator('.card[data-login]').last()).toHaveAttribute('data-login', outsider);
      const after = await counters(page);
      expect(after.members).toBe(before.members + 1);
      expect(after.contributors).toBe(before.contributors);
      await expect(page.locator(`#contributions-tbody tr[data-login="${outsider}"] td`).nth(1)).toHaveText('0');

      // "Unmark" on the card still removes the member (and the card)
      page.once('dialog', (d) => d.accept());
      await reloading(page, () => card.locator('.unmark-member-link').click());
      await expect(page.locator(`.card[data-login="${outsider}"]`)).toHaveCount(0);
      expect(await counters(page)).toEqual(before);
    } finally {
      await removeQuietly(page, outsider);
    }
  });

  test('Edit member: name and login, manual associations follow the new login; Remove from the table', async ({ page }) => {
    const { admin } = await open(page);
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS');
    const renamed = 'defunkt'; // exists on GitHub, never commits in the students' repositories
    const key = 'name:e2e-members-rename';
    await removeQuietly(page, outsider);
    await removeQuietly(page, renamed);

    try {
      expect((await page.request.post(members(), {
        data: { login: outsider, name: 'Octo', manual: true }, headers: ajax })).status()).toBe(200);
      expect((await page.request.post(aliases(), {
        data: { identityKey: key, login: outsider }, headers: ajax })).status()).toBe(200);

      await manage(page);

      // name only
      await row(page, outsider).locator('.edit-member-link').click();
      await expect(page.locator('#memberModalLabel')).toHaveText('Edit member');
      await expect(page.locator('#github-username')).toHaveValue(outsider);
      await expect(page.locator('#user-name')).toHaveValue('Octo');
      await page.locator('#user-name').fill('Octo Cat');
      await reloading(page, () => page.locator('#save-member').click());
      await expect(row(page, outsider).locator('.member-name')).toHaveText('Octo Cat');

      // nonexistent new login: error in the form, nothing changes
      await row(page, outsider).locator('.edit-member-link').click();
      await page.locator('#github-username').fill('e2e-user-that-does-not-exist-4242');
      await page.locator('#save-member').click();
      await expect(page.locator('#member-form-error')).toHaveText('This GitHub user does not exist.');
      await page.locator('#memberModal .btn-close').click();

      // new login: the association moves with the student
      await row(page, outsider).locator('.edit-member-link').click();
      await page.locator('#github-username').fill(renamed);
      await reloading(page, () => page.locator('#save-member').click());
      await expect(row(page, outsider)).toHaveCount(0);
      await expect(row(page, renamed).locator('.member-name')).toHaveText('Octo Cat');

      // Remove is blocked while the association (now of the new login) exists
      page.once('dialog', (d) => d.accept());
      await row(page, renamed).locator('.remove-member-link').click();
      await expect(page.locator('#members-alert')).toHaveText('Unlink the identities linked to this student before removing them.');

      // without the association, Remove works
      await page.request.delete(aliases(), { params: { identityKey: key }, headers: ajax });
      page.once('dialog', (d) => d.accept());
      await reloading(page, () => row(page, renamed).locator('.remove-member-link').click());
      await expect(row(page, renamed)).toHaveCount(0);
    } finally {
      await page.request.delete(aliases(), { params: { identityKey: key }, headers: ajax });
      await removeQuietly(page, outsider);
      await removeQuietly(page, renamed);
    }
  });

  test('removal is blocked while the student has manual associations', async ({ page }) => {
    const { admin } = await open(page);
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    const login = await page.locator('.unmark-member-link').first().getAttribute('data-login');
    test.skip(!login, 'no members');
    const key = 'name:e2e-members-block';

    const link = await page.request.post(aliases(), { data: { identityKey: key, login: login! }, headers: ajax });
    expect(link.status()).toBe(200);
    try {
      const response = await page.request.delete(`${members()}/${login}`, { headers: ajax });
      expect(response.status()).toBe(409);
      expect((await response.json()).message).toBe('Unlink the identities linked to this student before removing them.');
    } finally {
      await page.request.delete(aliases(), { params: { identityKey: key }, headers: ajax });
    }
  });

  test('API refuses calls without X-Requested-With (CSRF)', async ({ page }) => {
    await open(page);
    const response = await page.request.post(members(), { data: { login: outsider } });
    expect(response.status()).toBe(403);
    expect((await response.json()).message).toBe('Missing X-Requested-With header.');
  });

  test('non-administrators see no member management and get 403 from the API', async ({ page }) => {
    const { admin } = await open(page);
    test.skip(admin, 'the signed-in user is an administrator (GRADE_CHECKER_ADMINS)');

    await expect(page.locator('#manage-members-link, #add-member-link')).toHaveCount(0);
    await expect(page.locator('.mark-member-link, .unmark-member-link')).toHaveCount(0);
    const response = await page.request.post(members(), { data: { login: outsider }, headers: ajax });
    expect(response.status()).toBe(403);
    expect((await response.json()).message).toBe('Only administrators can manage members.');
    const edit = await page.request.put(`${members()}/${outsider}`, { data: { login: 'x' }, headers: ajax });
    expect(edit.status()).toBe(403);
    expect((await page.request.get(members())).status()).toBe(403);

    await page.goto(`/group/members?id=${aliasGroupId}`);
    await expect(page.locator('#members-page-error')).toHaveText('Only administrators can manage members.');
    await expect(page.locator('#members-table')).toHaveCount(0);
  });

});
