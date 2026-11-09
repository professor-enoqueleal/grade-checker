import { test, expect, Page } from '@playwright/test';
import { captureStats, chartState, expectChartsToMatch, expectTableToMatch } from '../../support/dashboard';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** Group dashboard on /detail (capabilities contributions-dashboard and contributions-api). */
test.describe('Group dashboard', () => {

  test.skip(!githubToken, skipWithoutToken);

  /** Row of a simulated report: the identity fields of the contract (author with an account by default). */
  const row = (r: Record<string, unknown>) => ({ identityKey: r.linked === false ? `name:${r.author}` : null, manualIdentities: [], ...r });

  /** Opening the group page loads the dashboard (All) without any click. */
  const openGroup = (page: Page) => captureStats(page, () => page.goto(`/detail?id=${groupId}`));

  test('dashboard loads automatically with All when the page opens', async ({ page }) => {
    const { response, body } = await openGroup(page);

    expect(response.status()).toBe(200);
    expect(new URL(response.url()).searchParams.get('period')).toBe('all');
    expect(body.period).toBe('all');

    await expect(page.locator('.period-button[data-period="all"]')).toHaveClass(/active/);
    await expectTableToMatch(page, body);
    await expect(page.locator('#dashboard-summary')).toContainText(`${body.totalCommits} commits · All history`);
    await expectChartsToMatch(page, body);
  });

  test('there is no Load Dashboard button anymore', async ({ page }) => {
    await openGroup(page);
    await expect(page.getByRole('button', { name: /Load Dashboard|Reload Dashboard/ })).toHaveCount(0);
  });

  test('period buttons are above the table and the charts; grades chart above the lines chart', async ({ page }) => {
    await openGroup(page);

    const buttons = await page.locator('#period-buttons').boundingBox();
    const table = await page.locator('#contributions-section').boundingBox();
    const grades = await page.locator('#grades-chart-section').boundingBox();
    const lines = await page.locator('#lines-chart-section').boundingBox();

    expect(buttons && table && grades && lines).toBeTruthy();
    expect(buttons!.y + buttons!.height).toBeLessThanOrEqual(table!.y);
    expect(table!.y + table!.height).toBeLessThanOrEqual(grades!.y);
    expect(grades!.y + grades!.height).toBeLessThanOrEqual(lines!.y);
  });

  test('table follows every period button, not only the chart', async ({ page }) => {
    await openGroup(page);

    const enabled = page.locator('.period-button:not([disabled])');
    const keys = await enabled.evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.period!));
    expect(keys).toContain('m1');

    for (const key of keys) {
      const { response, body } = await captureStats(page, () => page.locator(`.period-button[data-period="${key}"]`).click());

      expect(response.status(), key).toBe(200);
      expect(body.period).toBe(key);
      await expect(page.locator(`.period-button[data-period="${key}"]`)).toHaveClass(/active/);
      await expect(page.locator('#dashboard-summary')).toContainText(body.periodText);
      await expectTableToMatch(page, body);
      await expectChartsToMatch(page, body);   // grades (All and milestones) and lines changed, from the same response
    }
  });

  test('custom period sends local dates and updates the table', async ({ page }) => {
    await openGroup(page);

    await page.locator('#custom-period summary').click();
    await page.locator('#since').fill('2026-09-01T00:00');
    await page.locator('#until').fill('2026-09-30T23:59');

    const { response, body } = await captureStats(page, () => page.getByRole('button', { name: 'Apply' }).click());
    const params = new URL(response.url()).searchParams;

    expect(params.get('period')).toBe('custom');
    expect(params.get('since')).toBe('2026-09-01T00:00');
    expect(params.get('until')).toBe('2026-09-30T23:59');
    expect(body.periodText).toBe('Sep 1, 2026 00:00 – Sep 30, 2026 23:59'); // local time (America/Sao_Paulo)
    await expectTableToMatch(page, body);
    await expectChartsToMatch(page, body);   // custom: lines changed only
    await expect(page.locator('#grades-chart-section')).toBeHidden();
  });

  test('authors without GitHub account, and logins that received unlinked commits, get badges with the git identities', async ({ page }) => {
    // deterministic response: one linked author and one author whose commits are not linked to an account
    const fake = {
      period: 'all', periodText: 'All history', totalCommits: 3, truncated: false, grading: null,
      rows: [
        { author: 'joaolima16', commits: 2, totalAdditions: 10, totalDeletions: 2, percentage: 70, linked: true, gitIdentities: [] },
        { author: 'thiagofp77', commits: 2, totalAdditions: 5, totalDeletions: 0, percentage: 10, linked: true,
          gitIdentities: ['thiagofp77 <thiiago.fp7gmail.com>'] },
        { author: '<b>joaolima</b>', commits: 1, totalAdditions: 3, totalDeletions: 0, percentage: 20, linked: false,
          gitIdentities: ['joaolima <=>', 'JOAO VITOR <joao@escola.edu>'] },
      ].map(row),
    };
    await page.route(/\/api\/groups\/\d+\/contributions/, (route) => route.fulfill({ json: fake }));

    await page.goto(`/detail?id=${groupId}`);
    await expect(page.locator('#contributions-section')).toBeVisible();

    const linked = page.locator('#contributions-tbody tr', { has: page.locator('.author-name', { hasText: /^joaolima16$/ }) });
    await expect(linked.locator('.no-account-badge')).toHaveCount(0);

    const unlinked = page.locator('#contributions-tbody tr', { has: page.locator('.author-name', { hasText: '<b>joaolima</b>' }) });
    const badge = unlinked.locator('.no-account-badge');
    await expect(badge).toHaveText('No GitHub account');
    await expect(badge).toHaveAttribute('title', 'Commits not linked to a GitHub account: joaolima <=>, JOAO VITOR <joao@escola.edu>');
    await expect(unlinked.locator('b')).toHaveCount(0); // names are text, never HTML

    const merged = page.locator('#contributions-tbody tr', { has: page.locator('.author-name', { hasText: /^thiagofp77$/ }) });
    await expect(merged.locator('.no-account-badge')).toHaveCount(0);
    await expect(merged.locator('.includes-unlinked-badge')).toHaveText('Includes unlinked commits');
    await expect(merged.locator('.includes-unlinked-badge')).toHaveAttribute('title', 'Also counted here (commits not linked to the account): thiagofp77 <thiiago.fp7gmail.com>');
    await expect(linked.locator('.includes-unlinked-badge')).toHaveCount(0);
  });

  test('bar charts: students only, stacked milestones, in progress, period without lines, group without students', async ({ page }) => {
    // deterministic responses per period (openspec change graficos-e-explicacao-da-nota)
    const base = { truncated: false };
    const joao = { author: 'joao', commits: 1, totalAdditions: 3, totalDeletions: 0, percentage: 10, linked: false, gitIdentities: ['joao <=>'] };
    const responses: Record<string, unknown> = {
      all: { ...base, period: 'all', periodText: 'All history', totalCommits: 4,
        grading: { students: 2, maxPoints: 3, tolerance: 0.8, provisionalMilestones: ['m3'] },
        rows: [
          { author: 'alice', commits: 2, totalAdditions: 15, totalDeletions: 3, percentage: 60, linked: true, gitIdentities: [],
            milestoneGrades: { m1: 3, m2: 2.4, m3: null }, total: 5.4 },
          { author: 'bob', commits: 1, totalAdditions: 9, totalDeletions: 0, percentage: 30, linked: true, gitIdentities: [],
            milestoneGrades: { m1: 1, m2: 0.5, m3: 0.2 }, total: 1.7 },
          { ...joao, milestoneGrades: null, total: null },
        ].map(row) },
      m2: { ...base, period: 'm2', periodText: 'Milestone 2', totalCommits: 2,
        grading: { students: 2, maxPoints: 3, tolerance: 0.8, provisional: true, excludedCommits: 1 },
        rows: [
          { author: 'alice', commits: 1, totalAdditions: 5, totalDeletions: 1, percentage: 60, linked: true, gitIdentities: [], grade: 1.8 },
          { author: 'bob', commits: 0, totalAdditions: 0, totalDeletions: 0, percentage: 0, linked: true, gitIdentities: [], grade: 0 },
          { ...joao, grade: null },
        ].map(row) },
      m3: { ...base, period: 'm3', periodText: 'Milestone 3', totalCommits: 0,
        grading: { students: 2, maxPoints: 3, tolerance: 0.8, provisional: true },
        rows: [
          { author: 'alice', commits: 0, totalAdditions: 0, totalDeletions: 0, percentage: 0, linked: true, gitIdentities: [], grade: 0 },
          { author: 'bob', commits: 0, totalAdditions: 0, totalDeletions: 0, percentage: 0, linked: true, gitIdentities: [], grade: 0 },
        ].map(row) },
      m1: { ...base, period: 'm1', periodText: 'Milestone 1', totalCommits: 1,
        grading: { students: 0, maxPoints: 3, tolerance: 0.8, provisional: false },
        rows: [{ ...joao, grade: null }].map(row) },
    };
    await page.route(/\/api\/groups\/\d+\/contributions/, (route) => {
      const period = new URL(route.request().url()).searchParams.get('period') ?? 'all';
      return route.fulfill({ json: responses[period] });
    });

    // All: alice and bob (students) in the grades chart, joao only in the lines chart
    const all = await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
    await expectChartsToMatch(page, all.body);
    let grades = await chartState(page, 'grades-chart');
    expect(grades!.labels).toEqual(['alice', 'bob']);
    expect(grades!.datasets.map((d) => d.label)).toEqual(['M1', 'M2', 'M3 (in progress)']);
    expect(grades!.datasets.map((d) => d.data[0])).toEqual([3, 2.4, null]);     // alice: 5.40 of 9, no M3 segment
    expect((await chartState(page, 'lines-chart'))!.labels).toEqual(['alice', 'bob', 'joao (no account)']);

    // Milestone 2 in progress: one series, same color as M2 in All
    const m2 = await captureStats(page, () => page.locator('.period-button[data-period="m2"]').click());
    await expectChartsToMatch(page, m2.body);
    grades = await chartState(page, 'grades-chart');
    expect(grades!.title).toBe('Milestone 2 grade (0–3) (in progress)');
    expect(grades!.datasets[0].data).toEqual([1.8, 0]);

    // Milestone 3 without lines: grades (zeros) still shown, lines chart replaced by the message
    const m3 = await captureStats(page, () => page.locator('.period-button[data-period="m3"]').click());
    await expectChartsToMatch(page, m3.body);
    await expect(page.locator('#grades-chart-section')).toBeVisible();
    await expect(page.locator('#lines-chart-section')).toBeHidden();
    await expect(page.locator('#dashboard-alert')).toHaveText('No contributions found in this period.');

    // Milestone 1 with no students: grades chart hidden, the page explains it, the lines chart stays
    const m1 = await captureStats(page, () => page.locator('.period-button[data-period="m1"]').click());
    await expectChartsToMatch(page, m1.body);
    await expect(page.locator('#grades-chart-section')).toBeHidden();
    await expect(page.locator('#lines-chart-section')).toBeVisible();
    await expect(page.locator('#grading-alert')).toContainText('No students registered in this group');
  });

  test('page text is rendered as UTF-8', async ({ page }) => {
    await openGroup(page);
    expect(await page.locator('body').innerText()).not.toMatch(/Ã/);
  });
});
