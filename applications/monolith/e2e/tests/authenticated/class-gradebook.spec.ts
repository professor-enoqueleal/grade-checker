import { test, expect, Page } from '@playwright/test';
import { captureStats } from '../../support/dashboard';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** openspec capability class-gradebook (change notas-da-turma). */
test.describe('Class grades', () => {

  test.skip(!githubToken, skipWithoutToken);

  const fmt = (v: number | null | undefined) => (v === null || v === undefined ? '—' : v.toFixed(2));

  /** The signed-in user is an administrator when the group detail says so (data-admin, set by the server). */
  async function isAdmin(page: Page) {
    await page.goto(`/detail?id=${groupId}`);
    return (await page.locator('#content').getAttribute('data-admin')) === 'true';
  }

  async function waitForGrades(page: Page) {
    await expect(page.locator('#grades-tbody tr.grade-row.pending')).toHaveCount(0, { timeout: 180_000 });
  }

  test('Class grades is in the navbar, not on the breadcrumb line', async ({ page }) => {
    test.skip(!(await isAdmin(page)), 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    await page.goto('/home');
    await expect(page.locator('#main-navbar #class-grades-link')).toHaveText('Class grades');
    await expect(page.locator('.breadcrumb')).toHaveText('Home');                     // breadcrumb only with Home
    await expect(page.locator('main #class-grades-link')).toHaveCount(0);
    await page.locator('#class-grades-link').click();
    await expect(page).toHaveURL(/\/grades$/);
    await expect(page.locator('#class-grades-link')).toHaveAttribute('aria-current', 'page');
  });

  test('administrator sees every group, with the same grades as the group detail', async ({ page }) => {
    test.setTimeout(240_000);
    test.skip(!(await isAdmin(page)), 'the signed-in user is not in GRADE_CHECKER_ADMINS (set E2E_ADMIN_LOGIN / .env.local)');

    // grades of the group detail, period All
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${groupId}`));

    await page.goto('/home');
    await page.locator('#class-grades-link').click();
    await expect(page).toHaveURL(/\/grades$/);
    await expect(page.locator('h4')).toHaveText('Class grades');

    const groupCount = await page.locator('#grades-tbody tr.grade-row').evaluateAll(
      (rows) => new Set(rows.map((r) => (r as HTMLElement).dataset.groupId)).size);
    expect(groupCount).toBeGreaterThan(0);

    await waitForGrades(page);

    const provisional = body.grading?.provisionalMilestones ?? [];
    for (const key of provisional) {
      await expect(page.locator(`#header-${key}`)).toHaveText(`M${key.substring(1)} (in progress)`);
    }

    const students = body.stats.consolidatedStats.filter((s) => s.milestoneGrades);
    expect(students.length).toBeGreaterThan(0);
    for (const s of students) {
      const row = page.locator(`#grades-tbody tr.grade-row[data-group-id="${groupId}"]`)
        .filter({ has: page.locator('td.student-login', { hasText: new RegExp(`^${s.author}$`, 'i') }) });
      await expect(row, s.author).toHaveCount(1);
      const cells = (await row.locator('td.grade-cell').allInnerTexts()).map((t) => t.replace(' (in progress)', '').trim());
      expect(cells, s.author).toEqual([fmt(s.milestoneGrades!.m1), fmt(s.milestoneGrades!.m2), fmt(s.milestoneGrades!.m3), fmt(s.total)]);
    }
  });

  test('sorting by Total and filtering by text', async ({ page }) => {
    test.setTimeout(240_000);
    test.skip(!(await isAdmin(page)), 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    await page.goto('/grades');
    await waitForGrades(page);

    const totalHeader = page.locator('.sort-button[data-sort="total"]');
    await totalHeader.click();
    await totalHeader.click(); // descending
    await expect(totalHeader).toHaveAttribute('data-direction', 'desc');

    const totals = await page.locator('#grades-tbody tr.grade-row').evaluateAll((rows) => rows.map((r) => {
      const td = r.querySelector('td.grade-cell[data-key="total"]') as HTMLElement | null;
      return td && td.dataset.value ? Number(td.dataset.value) : null;
    }));
    const present = totals.filter((t): t is number => t !== null);
    expect(present).toEqual([...present].sort((a, b) => b - a));
    // missing totals only at the end
    expect(totals.slice(0, present.length).every((t) => t !== null)).toBe(true);

    const login = await page.locator('#grades-tbody tr.grade-row td.student-login').first().innerText();
    await page.locator('#grades-filter').fill(login.toUpperCase());
    const visible = page.locator('#grades-tbody tr.grade-row:not(.d-none)');
    expect(await visible.count()).toBeGreaterThan(0);
    for (const row of await visible.all()) {
      const text = ((await row.getAttribute('data-group-name')) + ' ' + (await row.getAttribute('data-login'))).toLowerCase();
      expect(text).toContain(login.toLowerCase());
    }
  });

  test('Export CSV downloads grades.csv in the pt-BR format', async ({ page }) => {
    test.setTimeout(240_000);
    test.skip(!(await isAdmin(page)), 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    await page.goto('/grades');
    await waitForGrades(page); // warm cache

    const [download] = await Promise.all([page.waitForEvent('download'), page.locator('#export-csv').click()]);
    expect(download.suggestedFilename()).toBe('grades.csv');

    const response = await page.request.get('/grades.csv');
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('text/csv');
    const csv = (await response.body()).toString('utf-8');
    expect(csv.charCodeAt(0)).toBe(0xfeff);
    const lines = csv.substring(1).split('\r\n').filter((l) => l !== '');
    expect(lines[0]).toBe('group;student;m1;m2;m3;total;provisional;error');
    expect(lines.length).toBeGreaterThan(1);
    for (const line of lines.slice(1)) {
      expect(line.split(';').length, line).toBeGreaterThanOrEqual(8);
      expect(line).toMatch(/^[^;]*;[^;]+;(\d,\d\d)?;(\d,\d\d)?;(\d,\d\d)?;(\d,\d\d)?;/);
    }
  });

  test('non-administrators get 403, no link, and still see grades in the group detail', async ({ page }) => {
    test.skip(await isAdmin(page), 'the signed-in user is an administrator (GRADE_CHECKER_ADMINS)');

    for (const path of ['/grades', '/grades/group?id=' + groupId, '/grades.csv']) {
      const response = await page.request.get(path);
      expect(response.status(), path).toBe(403);
    }
    const page403 = await page.goto('/grades');
    expect(page403?.status()).toBe(403);
    await expect(page.locator('#grades-forbidden')).toHaveText('Only administrators can see class grades.');

    await page.goto('/home');
    await expect(page.locator('#class-grades-link')).toHaveCount(0);

    // grades of the group detail stay visible to every signed-in user
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
    expect(body.grading).toBeTruthy();
    const h = await page.locator('.contributions-table thead th').allInnerTexts();
    expect(h.slice(-1)[0]).toBe('Total (0–9)');
  });

});
