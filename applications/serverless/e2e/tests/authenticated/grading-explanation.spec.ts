import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** "Como as notas são calculadas" on /detail (capability group-detail-page; numbers from GET /api/config). */
test.describe('Grading explanation', () => {

  test.skip(!githubToken, skipWithoutToken);

  /** key=value pairs of a properties file of the application (the numbers of the text come from them). */
  function properties(file: string): Record<string, string> {
    const text = fs.readFileSync(path.resolve(__dirname, '../../../lambda/src/main/resources', file), 'utf-8');
    const result: Record<string, string> = {};
    for (const line of text.split('\n')) {
      const m = line.match(/^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$/);
      if (m) result[m[1]] = m[2];
    }
    return result;
  }

  const ptPercent = (value: number) =>
    `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(Math.round(value * 1000) / 10)}%`;
  const ptDate = (iso: string) => iso.split('-').reverse().join('/');

  test('every user sees the explanation in Portuguese, with the numbers of the configuration', async ({ page }) => {
    const g = properties('grading.properties');
    const ms = properties('milestones.properties');
    const w = [Number(g['grading.weight.effectiveLines'] ?? 0.5), Number(g['grading.weight.activeDays'] ?? 0.3),
      Number(g['grading.weight.relevantCommits'] ?? 0.2)];
    const sum = w.reduce((a, b) => a + b, 0);

    await page.goto(`/detail?id=${groupId}`);
    const box = page.locator('#grading-explanation');

    // for every user (no admin check), at the end of the page, in Portuguese
    await expect(box).toBeVisible();
    await expect(box).toHaveAttribute('lang', 'pt-BR');
    await expect(page.locator('#grading-explanation-title')).toHaveText('Como as notas são calculadas');
    const last = await page.locator('main > *').last().getAttribute('id');
    expect(last).toBe('grading-explanation');

    // milestones with their dates (dd/MM/yyyy)
    for (const n of [1, 2, 3]) {
      const label = ms[`milestone.${n}.label`];
      if (!label) continue;
      await expect(page.locator('#grading-explanation-milestones')).toContainText(
        `${label}: de ${ptDate(ms[`milestone.${n}.start`])} a ${ptDate(ms[`milestone.${n}.end`])}`);
    }

    // the three factors with the normalized weights, the cap per commit and the minimum lines of a relevant commit
    const factors = page.locator('#grading-explanation-factors');
    await expect(factors).toContainText(`Linhas efetivas (peso ${ptPercent(w[0] / sum)})`);
    await expect(factors).toContainText(`Dias ativos (peso ${ptPercent(w[1] / sum)})`);
    await expect(factors).toContainText(`Commits relevantes (peso ${ptPercent(w[2] / sum)})`);
    await expect(factors).toContainText(`no máximo ${g['grading.commit.lineCap'] ?? '400'} linhas por commit`);
    await expect(factors).toContainText(`pelo menos ${g['grading.relevantCommit.minLines'] ?? '20'} linhas alteradas`);

    // worked example with 4 students, same formula as the grade
    const max = Number(g['grading.milestone.maxPoints'] ?? 3);
    const tolerance = Number(g['grading.tolerance'] ?? 0.8);
    const grade = (share: number) => new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      .format(Math.round(max * Math.min(1, (share / 0.25) / tolerance) * 100) / 100);
    await expect(page.locator('#grading-explanation-example')).toContainText(
      `num grupo de 4 alunos, a parte justa é 25%. Quem tem ${ptPercent(0.25 * tolerance)} ou mais do índice recebe ${grade(0.25 * tolerance)}; `
      + `quem tem ${ptPercent(0.25 * tolerance / 2)} recebe ${grade(0.25 * tolerance / 2)}.`);

    // the rest of the page stays in English
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.getByRole('heading', { name: 'Group information' })).toBeVisible();
  });
});
