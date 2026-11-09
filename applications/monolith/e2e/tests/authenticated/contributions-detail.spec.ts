import { test, expect, Page } from '@playwright/test';
import { aliasGroupId, githubToken, groupId, skipWithoutToken } from '../../support/env';
import { captureStats } from '../../support/dashboard';

/** Commits of one contributor (/contributions-detail), openspec: filtro-por-marcos. */
test.describe('Contributions detail', () => {

  test.skip(!githubToken, skipWithoutToken);

  async function openFirstContributor(page: Page) {
    await page.goto(`/detail?id=${groupId}`);
    await page.getByRole('link', { name: 'Details', exact: true }).first().click();
    await expect(page.getByRole('heading', { name: 'Commits Details' })).toBeVisible();
  }

  async function totalMatchesRows(page: Page) {
    const total = Number(await page.locator('#total-of-commits').innerText());
    if (total === 0) {
      await expect(page.getByText('No commits found for this period')).toBeVisible();
    } else {
      await expect(page.locator('tbody tr')).toHaveCount(total);
    }
  }

  test('opens with All selected and lists every commit', async ({ page }) => {
    await openFirstContributor(page);

    await expect(page.locator('.period-filter .active')).toHaveText('All');
    await expect(page.locator('#period-text')).toHaveText('All history (merge commits excluded)');
    await totalMatchesRows(page);
  });

  test('milestone button filters by its dates and keeps the filter in the URL', async ({ page }) => {
    await openFirstContributor(page);

    await page.locator('.period-filter').getByRole('link', { name: 'Milestone 1' }).click();

    await expect(page).toHaveURL(/period=m1/);
    await expect(page.locator('.period-filter .active')).toHaveText('Milestone 1');
    await expect(page.locator('#period-text')).toHaveText('Aug 13, 2026 – Sep 17, 2026 (merge commits excluded)');
    await expect(page.locator('#since')).toHaveValue('2026-08-13T00:00');
    await expect(page.locator('#until')).toHaveValue('2026-09-17T23:59');
    await totalMatchesRows(page);
  });

  test('custom period is applied in local time', async ({ page }) => {
    await openFirstContributor(page);

    await page.locator('#custom-period summary').click();
    await page.locator('#since').fill('2026-09-01T00:00');
    await page.locator('#until').fill('2026-09-30T23:59');
    await page.getByRole('button', { name: 'Apply' }).click();

    await expect(page).toHaveURL(/period=custom/);
    await expect(page.locator('#custom-period')).toHaveAttribute('open', '');
    await expect(page.locator('#period-text')).toHaveText('Sep 1, 2026 00:00 – Sep 30, 2026 23:59 (merge commits excluded)');
    await totalMatchesRows(page);
  });

  test('commit links only point to GitHub', async ({ page }) => {
    await openFirstContributor(page);

    const hrefs = await page.locator('tbody a').evaluateAll((as) => as.map((a) => a.getAttribute('href')));
    for (const href of hrefs) {
      expect(href).toMatch(/^https:\/\/github\.com\//);
    }
  });

  // ---------- one-line messages and short hash (openspec change historico-mensagem-e-hash-curtos) ----------

  for (const width of [360, 1280]) {
    test(`messages in one line with the full text in the title, short hash (${width}px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await openFirstContributor(page);
      const rows = page.locator('tbody tr');
      test.skip(await rows.count() === 0, 'no commits for this contributor');

      const messages = await page.locator('.commit-message-text').evaluateAll((cells) => cells.map((cell) => {
        const range = document.createRange();
        range.selectNodeContents(cell);
        const lines = new Set(Array.from(range.getClientRects()).map((r) => Math.round(r.top))).size;
        return { lines, text: cell.textContent ?? '', title: cell.getAttribute('title') ?? '',
                 ellipsis: getComputedStyle(cell).textOverflow };
      }));
      for (const m of messages) {
        expect(m.lines).toBe(1);
        expect(m.ellipsis).toBe('ellipsis');
        expect(m.title).toBe(m.text);             // the full message is always in the title
      }

      const hashes = await page.locator('.commit-sha').evaluateAll((els) => els.map((el) => ({
        text: (el.textContent ?? '').trim(), title: el.getAttribute('title') ?? '' })));
      expect(hashes.length).toBe(await rows.count());
      for (const h of hashes) {
        expect(h.text).toMatch(/^[0-9a-f]{7}$/);
        expect(h.title).toMatch(/^[0-9a-f]{40}$/);
        expect(h.title.startsWith(h.text)).toBe(true);
      }

      // date: one line on wide screens; on phones, the date and the time take at most one line each
      const dateLines = await page.locator('td.commit-date').evaluateAll((cells) => cells.map((cell) => {
        const range = document.createRange();
        range.selectNodeContents(cell);
        return new Set(Array.from(range.getClientRects()).map((r) => Math.round(r.top))).size;
      }));
      for (const lines of dateLines) expect(lines).toBeLessThanOrEqual(width < 576 ? 2 : 1);

      // phones: the Identity column is hidden; identities (when any) are shown below the message
      await expect(page.locator('th', { hasText: 'Identity' })).toBeVisible({ visible: width >= 576 });
      const identities = await page.locator('td.commit-identity').allTextContents();
      const inline = await page.locator('.commit-identity-inline').count();
      expect(inline).toBe(identities.filter((t) => t.trim() !== '—').length);
      if (inline > 0) await expect(page.locator('.commit-identity-inline').first()).toBeVisible({ visible: width < 576 });

      // no horizontal scroll: neither the page nor the table itself (it fits its container)
      expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
      expect(await page.locator('.table-responsive').evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(false);
    });
  }

  // ---------- same attribution as the dashboard (openspec change historico-commits-atribuicao) ----------

  async function historyOf(page: Page, author: string, period: string, gid: string) {
    const owner = await page.locator('#content').getAttribute('data-owner');
    const repo = await page.locator('#content').getAttribute('data-repo');
    const params = new URLSearchParams({ author, owner: owner!, repo: repo!, groupId: gid, period });
    await page.goto(`/contributions-detail?${params}`);
    await expect(page.getByRole('heading', { name: 'Commits Details' })).toBeVisible();
    return Number(await page.locator('#total-of-commits').innerText());
  }

  // ---------- commit frequency chart (openspec change grafico-frequencia-commits) ----------

  test('frequency chart: same commits as the table, per day or per week, active days and milestone colors', async ({ page }) => {
    const colors: Record<string, string> = { m1: '#86b6ef', m2: '#3987e5', m3: '#1c5cab', outside: '#868e96' };
    await page.goto(`/detail?id=${groupId}`);
    const author = (await page.locator('.card[data-login]').first().getAttribute('data-login'))!;
    const owner = (await page.locator('#content').getAttribute('data-owner'))!;
    const repo = (await page.locator('#content').getAttribute('data-repo'))!;
    let checked = 0;

    for (const period of ['all', 'm1', 'm2', 'm3']) {
      await page.goto(`/contributions-detail?${new URLSearchParams({ author, owner, repo, groupId, period })}`);
      const total = Number(await page.locator('#total-of-commits').innerText());
      const chart = page.locator('#commit-activity');
      if (total === 0) {
        await expect(chart).toHaveCount(0);   // no commits: no chart
        continue;
      }
      checked++;
      await expect(chart).toBeVisible();

      const state = await page.evaluate(() => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const c = (window as any).Chart.getChart('commit-activity-chart');
        const data = JSON.parse(document.getElementById('commit-activity-data')!.textContent!);
        return {
          granularity: data.granularity as string, starts: data.starts as string[],
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          datasets: c.data.datasets.map((d: any) => ({ key: d.key, data: d.data as number[], color: String(d.backgroundColor).toLowerCase() })),
          legend: c.options.plugins.legend.display as boolean,
        };
      });

      // the columns add up to Total of commits
      const sum = state.datasets.reduce((acc, d) => acc + d.data.reduce((a, b) => a + b, 0), 0);
      expect(sum, period).toBe(total);

      // active days = distinct dates of the table (same time zone)
      const days = await page.locator('td.commit-date span:first-child').allInnerTexts();
      const active = new Set(days.map((d) => d.trim())).size;
      await expect(page.locator('#commit-activity-summary'))
        .toHaveText(`${total} ${total === 1 ? 'commit' : 'commits'} · ${active} ${active === 1 ? 'active day' : 'active days'}`);

      // a milestone is at most 62 days: per day; All of a whole semester: per week
      if (period.startsWith('m')) {
        expect(state.granularity).toBe('day');
        await expect(page.locator('#commit-activity-title')).toHaveText('Commits per day');
        expect(state.datasets.map((d) => d.key)).toEqual([period]);   // only that milestone, no legend
        expect(state.legend).toBe(false);
      } else {
        // All: from the day of the first commit (last row of the table) to today
        const first = Date.parse(days[days.length - 1]);
        const today = Date.parse(new Date().toDateString());
        const span = Math.round((today - first) / 86_400_000) + 1;
        expect(state.granularity, `${span} days`).toBe(span <= 62 ? 'day' : 'week');
        await expect(page.locator('#commit-activity-title')).toHaveText(span <= 62 ? 'Commits per day' : 'Commits per week');
        expect(state.legend).toBe(state.datasets.length > 1);
      }

      // colors of the milestones of the group dashboard
      for (const d of state.datasets) expect(d.color, d.key).toBe(colors[d.key]);
    }
    expect(checked, `${author} has commits in some period`).toBeGreaterThan(0);
  });

  test('frequency chart fits the page on a phone', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 800 });
    await openFirstContributor(page);
    test.skip(await page.locator('#commit-activity').count() === 0, 'no commits for this contributor');
    const box = await page.locator('#commit-activity-chart').boundingBox();
    expect(box!.width).toBeLessThanOrEqual(360);
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
  });

  for (const period of ['all', 'm2']) {
    test(`total of commits equals the dashboard Commits for every login (${period})`, async ({ page }) => {
      await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
      const { body } = await captureStats(page, () => page.locator(`.period-button[data-period="${period}"]`).click());
      const logins = body.stats.consolidatedStats.filter((s) => s.linked !== false && s.commits > 0);
      expect(logins.length).toBeGreaterThan(0);

      for (const stat of logins) {
        if (page.url().includes('/contributions-detail')) await page.goto(`/detail?id=${groupId}`);
        await expect(page.locator('#content')).toBeAttached();
        expect(await historyOf(page, stat.author, period, groupId), stat.author).toBe(stat.commits);
        await totalMatchesRows(page);
      }
    });
  }

  test('commits counted from other identities show them in Identity and Also counted', async ({ page }) => {
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${aliasGroupId}`));
    const withIdentities = body.stats.consolidatedStats.find((s) => s.linked !== false && (s.gitIdentities ?? []).length > 0);
    test.skip(!withIdentities, `group ${aliasGroupId} has no login with unlinked commits in All`);

    await historyOf(page, withIdentities!.author, 'all', aliasGroupId);

    const alsoCounted = await page.locator('#also-counted').innerText();
    for (const identity of withIdentities!.gitIdentities!) expect(alsoCounted).toContain(identity);
    const identities = await page.locator('td.commit-identity').allInnerTexts();
    expect(identities.some((t) => t.trim() !== '—')).toBe(true);
    expect(identities.some((t) => t.trim() === '—')).toBe(true);
  });

  test('manual association appears in the history with the manual badge', async ({ page }) => {
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${aliasGroupId}`));
    const admin = (await page.locator('#content').getAttribute('data-admin')) === 'true';
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS');

    const unlinked = body.stats.consolidatedStats.find((s) => s.linked === false && (s as { identityKey?: string }).identityKey);
    test.skip(!unlinked, `group ${aliasGroupId} has no commits without account in All`);
    const key = (unlinked as { identityKey: string }).identityKey;
    const student = (await page.locator('#link-identity-login option').first().getAttribute('value'))!;
    const ajax = { 'X-Requested-With': 'XMLHttpRequest' };

    try {
      const linked = await page.request.post('/identity-aliases', { form: { groupId: aliasGroupId, identityKey: key, login: student }, headers: ajax });
      expect(linked.status()).toBe(200);

      await historyOf(page, student, 'all', aliasGroupId);

      await expect(page.locator('#also-counted')).toContainText('(manual)');
      await expect(page.locator('td.commit-identity .manual-badge').first()).toHaveText('manual');
      expect(await page.locator('td.commit-identity .manual-badge').count()).toBe(unlinked!.commits);
    } finally {
      await page.request.delete('/identity-aliases', { params: { groupId: aliasGroupId, identityKey: key }, headers: ajax });
    }
  });
});
