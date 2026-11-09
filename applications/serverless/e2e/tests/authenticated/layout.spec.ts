import { test, expect, Page } from '@playwright/test';
import { captureStats } from '../../support/dashboard';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** Responsive card grids (openspec/changes/cards-responsivos). */
test.describe('Responsive layout', () => {

  test.skip(!githubToken, skipWithoutToken);

  /** viewport width -> expected cards per row on home and on detail (Bootstrap sm 576, md 768, lg 992) */
  const widths: Array<[number, number, number]> = [[360, 1, 1], [576, 2, 2], [800, 2, 3], [1000, 3, 4], [1280, 3, 4]];

  /** card footers whose links do not fit in one line (a link split in two lines, or dropped to a second line) */
  async function wrappedFooters(page: Page): Promise<string[]> {
    return page.evaluate(() => Array.from(document.querySelectorAll('main .card-footer')).filter((f) => {
      const links = Array.from(f.querySelectorAll('a.card-link'));
      return links.some((l) => l.getClientRects().length !== 1)
        || new Set(links.map((l) => Math.round(l.getBoundingClientRect().top))).size > 1;
    }).map((f) => f.closest('.card')?.getAttribute('data-login') ?? '?'));
  }

  interface Grid { cards: number; overlaps: number; perRow: number; horizontalScroll: boolean; footersAligned: boolean }

  async function measureGrid(page: Page): Promise<Grid> {
    return page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('main .card')).map((c) => c.getBoundingClientRect());
      let overlaps = 0;
      for (let i = 0; i < cards.length; i++) {
        for (let j = i + 1; j < cards.length; j++) {
          const a = cards[i], b = cards[j];
          if (a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1) overlaps++;
        }
      }
      const firstTop = cards.length ? Math.round(cards[0].top) : 0;
      const firstRow = Array.from(document.querySelectorAll('main .card')).filter((c) => Math.round(c.getBoundingClientRect().top) === firstTop);
      const footerTops = new Set(firstRow.map((c) => Math.round(c.querySelector('.card-footer')!.getBoundingClientRect().top)));
      return {
        cards: cards.length,
        overlaps,
        perRow: firstRow.length,
        horizontalScroll: document.documentElement.scrollWidth > window.innerWidth,
        footersAligned: footerTops.size === 1,
      };
    });
  }

  for (const [width, columns, detailColumns] of widths) {

    test(`home: ${columns} column(s) at ${width}px, no overlap`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/home');
      await expect(page.locator('main .card').first()).toBeVisible();   // the SPA loads the groups after the page

      const grid = await measureGrid(page);
      expect(grid.cards).toBeGreaterThan(1);
      expect(grid.overlaps).toBe(0);
      expect(grid.horizontalScroll).toBe(false);
      expect(grid.perRow).toBe(Math.min(columns, grid.cards));
      expect(grid.footersAligned).toBe(true);
    });

    test(`detail: ${detailColumns} column(s) at ${width}px, no overlap, table fits`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await captureStats(page, () => page.goto(`/detail?id=${groupId}`)); // dashboard table rendered too

      const grid = await measureGrid(page);
      expect(grid.overlaps).toBe(0);
      expect(grid.horizontalScroll).toBe(false);
      expect(grid.perRow).toBe(Math.min(detailColumns, grid.cards));
      expect(grid.footersAligned).toBe(true);
      expect(await wrappedFooters(page)).toEqual([]);   // GitHub, Details and (admin) Mark/Unmark in one line
      // bar charts take the width of the page (no empty side columns as the old doughnut had)
      const table = await page.locator('#contributions-section').boundingBox();
      const chart = await page.locator('#lines-chart-section').boundingBox();
      expect(table && chart).toBeTruthy();
      expect(Math.abs(chart!.width - table!.width)).toBeLessThanOrEqual(2);
    });
  }

  for (const width of [800, 1280]) {
    test(`detail: avatars are square card-img-top, edge to edge and compact at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await captureStats(page, () => page.goto(`/detail?id=${groupId}`));

      const images = await page.locator('main .card img').evaluateAll((imgs) => imgs.map((i) => {
        const r = i.getBoundingClientRect();
        const card = i.closest('.card')!.getBoundingClientRect();
        return { w: r.width, h: r.height, cardW: card.width, radius: getComputedStyle(i).borderTopLeftRadius,
                 topImg: i.classList.contains('card-img-top') };
      }));
      expect(images.length).toBeGreaterThan(0);
      for (const img of images) {
        expect(img.topImg).toBe(true);
        expect(Math.abs(img.w - img.h)).toBeLessThan(1.5);        // square
        expect(Math.abs(img.w - img.cardW)).toBeLessThan(3);      // edge to edge (card border)
        expect(img.w).toBeLessThanOrEqual(270);                   // compact (was ~356px)
        expect(img.radius).not.toBe('50%');                       // not a circle
      }
    });
  }

  test('images have descriptive alt text', async ({ page }) => {
    await page.goto('/home');
    await expect(page.locator('main .card').first()).toBeVisible();
    await expect(page.locator('main .card img').first()).toHaveAttribute('alt', /^Group \d+$/);

    await captureStats(page, () => page.goto(`/detail?id=${groupId}`));
    const alts = await page.locator('main .card img').evaluateAll((imgs) => imgs.map((i) => i.getAttribute('alt')));
    for (const alt of alts) expect(alt).toMatch(/^Avatar of \S+/);
  });
});
