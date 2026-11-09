import { test, expect, Page } from '@playwright/test';
import { captureStats, StatsResponse } from '../../support/dashboard';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** openspec capability milestone-grading (change nota-por-marco). */
test.describe('Milestone grades', () => {

  test.skip(!githubToken, skipWithoutToken);

  const headers = (page: Page) => page.locator('.contributions-table thead th').allInnerTexts();

  async function gradeCells(page: Page) {
    return page.locator('#contributions-tbody tr').evaluateAll((trs) =>
      trs.map((tr) => Array.from(tr.querySelectorAll('td.grade-cell')).map((td) => (td.textContent ?? '').trim())));
  }

  const fmt = (v: number | null | undefined) => (v === null || v === undefined ? '—' : v.toFixed(2));

  test('milestone period shows Grade (0–3) matching the response', async ({ page }) => {
    await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
    const { body } = await captureStats(page, () => page.locator('.period-button[data-period="m2"]').click());

    expect(body.grading?.students).toBeGreaterThan(0);
    const h = await headers(page);
    expect(h[h.length - 1]).toMatch(/^Grade \(0–3\)/);

    const cells = await gradeCells(page);
    body.rows.forEach((s, i) => {
      if (s.grade !== null && s.grade !== undefined) {
        expect(s.grade).toBeGreaterThanOrEqual(0);
        expect(s.grade).toBeLessThanOrEqual(3);
      }
      expect(cells[i][0].replace(' (in progress)', ''), s.author).toBe(fmt(s.grade));
    });
  });

  test('All shows M1, M2, M3 and Total = sum of the milestones', async ({ page }) => {
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${groupId}`));

    const h = await headers(page);
    expect(h.slice(-4).map((x) => x.replace(' (in progress)', ''))).toEqual(['M1', 'M2', 'M3', 'Total (0–9)']);

    const cells = await gradeCells(page);
    body.rows.forEach((s: StatsResponse['rows'][number], i) => {
      if (!s.milestoneGrades) return;
      const sum = ['m1', 'm2', 'm3'].reduce((acc, k) => acc + (s.milestoneGrades![k] ?? 0), 0);
      expect(s.total, s.author).toBeCloseTo(sum, 2);
      expect(s.total!).toBeLessThanOrEqual(9);
      expect(cells[i], s.author).toEqual([fmt(s.milestoneGrades.m1), fmt(s.milestoneGrades.m2), fmt(s.milestoneGrades.m3), fmt(s.total)]);
    });
  });

  test('milestone in progress is marked', async ({ page }) => {
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
    for (const key of body.grading?.provisionalMilestones ?? []) {
      expect(await headers(page)).toContain(`M${key.substring(1)} (in progress)`);
    }
  });

  test('custom period has no grade columns', async ({ page }) => {
    await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
    await page.locator('#custom-period summary').click();
    await page.locator('#since').fill('2026-09-01T00:00');
    await page.locator('#until').fill('2026-09-30T23:59');
    await captureStats(page, () => page.getByRole('button', { name: 'Apply' }).click());

    expect(await page.locator('.contributions-table thead th.grade-col').count()).toBe(0);
  });
});
