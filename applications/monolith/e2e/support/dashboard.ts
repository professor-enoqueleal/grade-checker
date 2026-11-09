import { expect, Page, Response } from '@playwright/test';

export interface StatsResponse {
  message: string;
  period: string;
  periodText: string;
  since: string | null;
  until: string;
  totalCommits: number;
  truncated: boolean;
  stats: { consolidatedStats: Array<{ author: string; commits: number; totalAdditions: number; totalDeletions: number; percentage: number;
    linked?: boolean; gitIdentities?: string[]; grade?: number | null; milestoneGrades?: Record<string, number | null> | null; total?: number | null }> };
  grading?: { students: number; maxPoints?: number; fairShare?: number; excludedCommits?: number; provisional?: boolean; provisionalMilestones?: string[] };
}

/** Runs `action` and returns the /contributors-stats response it triggered (status + JSON). */
export async function captureStats(page: Page, action: () => Promise<unknown>): Promise<{ response: Response; body: StatsResponse }> {
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/contributors-stats'), { timeout: 60_000 }),
    action(),
  ]);
  const body = (await response.json()) as StatsResponse;
  // the page renders after the response; wait until the loader is gone
  await expect(page.locator('#loader')).toBeHidden();
  return { response, body };
}

/** Text of each table row: "author | commits | additions | deletions | percentage" (author = name only, without the badge). */
export async function tableRows(page: Page): Promise<string[][]> {
  return page.locator('#contributions-tbody tr').evaluateAll((trs) =>
    trs.map((tr) => Array.from(tr.querySelectorAll('td')).map((td, i) => {
      const name = i === 0 ? td.querySelector('.author-name') : null;
      return ((name ?? td).textContent ?? '').trim();
    })),
  );
}

/** The table must show exactly the authors of the response (in order), followed by group contributors with zeros. */
export async function expectTableToMatch(page: Page, body: StatsResponse) {
  const expected = body.stats.consolidatedStats.map((s) => [
    s.author,
    String(s.commits),
    String(s.totalAdditions),
    String(s.totalDeletions),
    `${s.percentage.toFixed(2)}%`,
  ]);

  await expect(page.locator('#contributions-section')).toBeVisible();

  const rows = (await tableRows(page)).map((r) => r.slice(0, 5)); // grade columns are checked separately
  expect(rows.slice(0, expected.length)).toEqual(expected);

  for (const row of rows.slice(expected.length)) {
    expect(row.slice(1)).toEqual(['0', '0', '0', '0.00%']);
  }

  // "No GitHub account" badge only on unlinked authors
  const badges = await page.locator('#contributions-tbody tr').evaluateAll((trs) => trs.map((tr) => tr.querySelector('.no-account-badge') !== null));
  body.stats.consolidatedStats.forEach((s, i) => expect(badges[i], s.author).toBe(s.linked === false));

  // "Includes unlinked commits" badge only on linked authors that received unlinked commits
  const includes = await page.locator('#contributions-tbody tr').evaluateAll((trs) => trs.map((tr) => tr.querySelector('.includes-unlinked-badge') !== null));
  body.stats.consolidatedStats.forEach((s, i) =>
    expect(includes[i], s.author).toBe(s.linked !== false && (s.gitIdentities ?? []).length > 0));
}

/** Colors of the series of the bar charts (openspec change graficos-e-explicacao-da-nota). */
export const chartColors = { m1: '#86b6ef', m2: '#3987e5', m3: '#1c5cab', additions: '#eb6834', deletions: '#6250d6' };

interface ChartState {
  visible: boolean;
  title: string;
  labels: string[];
  datasets: Array<{ label: string; data: Array<number | null>; color: string }>;
  max: number | undefined;
}

/** State of a bar chart of the page, read from Chart.js (null when the canvas has no chart). */
export async function chartState(page: Page, id: 'grades-chart' | 'lines-chart'): Promise<ChartState | null> {
  return page.evaluate((canvasId) => {
    const section = document.getElementById(`${canvasId}-section`)!;
    const visible = !section.classList.contains('d-none');
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const chart = (window as any).Chart.getChart(canvasId);
    if (!chart) return visible ? { visible, title: '', labels: [], datasets: [], max: undefined } : null;
    return {
      visible,
      title: (document.getElementById(`${canvasId}-title`)!.textContent ?? '').trim(),
      labels: chart.data.labels as string[],
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      datasets: chart.data.datasets.map((d: any) => ({ label: d.label, data: d.data, color: String(d.backgroundColor).toLowerCase() })),
      max: chart.options.scales.x.max,
    };
  }, id);
}

/** Both bar charts must show exactly the data of the response for its period. */
export async function expectChartsToMatch(page: Page, body: StatsResponse) {
  const stats = body.stats.consolidatedStats;
  const period = body.period;
  const max = body.grading?.maxPoints ?? 3;

  // grades: students only (authors with a grade), All and milestones
  const grades = await chartState(page, 'grades-chart');
  const students = !body.grading || period === 'custom' ? []
    : stats.filter((s) => (period === 'all' ? s.total : s.grade) !== null && (period === 'all' ? s.total : s.grade) !== undefined);
  if (students.length === 0) {
    expect(grades === null || !grades.visible, `grades chart hidden (${period})`).toBe(true);
  } else {
    expect(grades, `grades chart (${period})`).not.toBeNull();
    expect(grades!.visible).toBe(true);
    expect(grades!.labels).toEqual(students.map((s) => s.author));
    if (period === 'all') {
      expect(grades!.title).toBe(`Grades by milestone (0–${max * 3})`);
      expect(grades!.max).toBe(max * 3);
      expect(grades!.datasets.map((d) => d.color)).toEqual([chartColors.m1, chartColors.m2, chartColors.m3]);
      ['m1', 'm2', 'm3'].forEach((key, i) => {
        expect(grades!.datasets[i].label.startsWith(`M${i + 1}`)).toBe(true);
        expect(grades!.datasets[i].label.includes('(in progress)')).toBe((body.grading!.provisionalMilestones ?? []).includes(key));
        expect(grades!.datasets[i].data).toEqual(students.map((s) => s.milestoneGrades ? s.milestoneGrades[key] ?? null : null));
      });
    } else {
      const n = period.substring(1);
      expect(grades!.title).toBe(`Milestone ${n} grade (0–${max})${body.grading!.provisional ? ' (in progress)' : ''}`);
      expect(grades!.max).toBe(max);
      expect(grades!.datasets).toHaveLength(1);
      expect(grades!.datasets[0].color).toBe(chartColors[period as 'm1' | 'm2' | 'm3']);   // same color as in All
      expect(grades!.datasets[0].data).toEqual(students.map((s) => s.grade));
    }
  }

  // lines changed: every author, every period
  const lines = await chartState(page, 'lines-chart');
  const total = stats.reduce((sum, s) => sum + s.totalAdditions + s.totalDeletions, 0);
  if (stats.length === 0 || total === 0) {
    expect(lines === null || !lines.visible, `lines chart hidden (${period})`).toBe(true);
    await expect(page.locator('#dashboard-alert')).toHaveText('No contributions found in this period.');
  } else {
    expect(lines, `lines chart (${period})`).not.toBeNull();
    expect(lines!.visible).toBe(true);
    expect(lines!.title).toBe('Lines changed (additions + deletions)');
    expect(lines!.labels).toEqual(stats.map((s) => s.author + (s.linked === false ? ' (no account)' : '')));
    expect(lines!.datasets.map((d) => [d.label, d.color])).toEqual([['Additions', chartColors.additions], ['Deletions', chartColors.deletions]]);
    expect(lines!.datasets[0].data).toEqual(stats.map((s) => s.totalAdditions));
    expect(lines!.datasets[1].data).toEqual(stats.map((s) => s.totalDeletions));
  }

  // no color stands for two series on the page
  const colors = [...(grades?.visible ? grades.datasets : []), ...(lines?.visible ? lines.datasets : [])].map((d) => d.color);
  expect(new Set(colors).size).toBe(colors.length);

  // never more than one chart per canvas (switching periods does not duplicate them)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  expect(await page.evaluate(() => Object.keys((window as any).Chart.instances).length)).toBeLessThanOrEqual(2);
}

