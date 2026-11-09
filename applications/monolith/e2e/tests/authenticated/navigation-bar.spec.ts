import { test, expect, Page } from '@playwright/test';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** openspec capability navigation-bar (change barra-de-navegacao). Never clicks "Sign out": the session is shared. */
test.describe('Navigation bar', () => {

  test.skip(!githubToken, skipWithoutToken);

  async function context(page: Page) {
    await page.goto(`/detail?id=${groupId}`);
    const content = page.locator('#content');
    const admin = (await content.getAttribute('data-admin')) === 'true';
    const owner = (await content.getAttribute('data-owner'))!;
    const repo = (await content.getAttribute('data-repo'))!;
    const author = (await page.locator('.card[data-login]').first().getAttribute('data-login'))!;
    return { admin, owner, repo, author };
  }

  test('every authenticated page has the same navbar, with the breadcrumb below it', async ({ page }) => {
    const { admin, owner, repo, author } = await context(page);
    const pages: Array<[string, string | null]> = [
      ['/home', null],
      [`/detail?id=${groupId}`, null],
      [`/contributions-detail?${new URLSearchParams({ author, owner, repo, groupId })}`, null],
      [`/group/members?id=${groupId}`, null],
      ['/grades', 'class-grades-link'],
      ['/groups', 'manage-groups-link'],
    ];

    for (const [url, active] of pages) {
      await page.goto(url);
      const navbar = page.locator('#main-navbar');
      await expect(navbar, url).toBeVisible();
      await expect(navbar).toHaveAttribute('aria-label', 'Main navigation');

      // brand to the home, no separate "Home" item
      await expect(navbar.locator('#navbar-brand')).toHaveText('Grade Checker');
      await expect(navbar.locator('#navbar-brand')).toHaveAttribute('href', '/home');
      await expect(navbar.locator('.nav-link', { hasText: /^Home$/ })).toHaveCount(0);

      // administrator links only for the administrator; current page marked
      await expect(navbar.locator('#manage-groups-link')).toHaveCount(admin ? 1 : 0);
      await expect(navbar.locator('#class-grades-link')).toHaveCount(admin ? 1 : 0);
      if (admin) {
        for (const id of ['manage-groups-link', 'class-grades-link']) {
          const link = navbar.locator(`#${id}`);
          if (id === active) await expect(link, `${url} ${id}`).toHaveAttribute('aria-current', 'page');
          else await expect(link, `${url} ${id}`).not.toHaveAttribute('aria-current', 'page');
        }
      }

      // login and Sign out for everybody
      await expect(navbar.locator('#navbar-user')).not.toBeEmpty();
      await expect(navbar.locator('#sign-out-link')).toHaveAttribute('href', '/logout');

      // the breadcrumb is still there, below the navbar
      const nav = (await navbar.boundingBox())!;
      const crumb = (await page.locator('nav[aria-label="breadcrumb"]').boundingBox())!;
      expect(crumb.y, url).toBeGreaterThanOrEqual(nav.y + nav.height);

      // Bootstrap JS exactly once, in any form (two copies break the phone menu: it opens but never closes)
      expect(await page.locator('script[src*="/bootstrap/"]').count(), url).toBe(1);
    }
  });

  test('the brand goes to the home', async ({ page }) => {
    await page.goto('/grades');
    await page.locator('#navbar-brand').click();
    await expect(page).toHaveURL(/\/home$/);
    await expect(page.locator('.breadcrumb')).toHaveText('Home');
  });

  test('on a phone the menu button opens and closes the links on every page, without script errors', async ({ page }) => {
    const { admin, owner, repo, author } = await context(page);
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    await page.setViewportSize({ width: 360, height: 800 });

    for (const url of ['/home', `/detail?id=${groupId}`, `/contributions-detail?${new URLSearchParams({ author, owner, repo, groupId })}`,
      `/group/members?id=${groupId}`, '/grades', '/groups']) {
      errors.length = 0;
      await page.goto(url);
      const toggler = page.locator('#main-navbar .navbar-toggler');
      await expect(toggler, url).toBeVisible();
      await expect(toggler).toHaveAttribute('aria-label', 'Toggle navigation');
      await expect(page.locator('#sign-out-link')).toBeHidden();

      // opens
      await toggler.click();
      await expect(page.locator('#sign-out-link'), `${url} open`).toBeVisible();
      await expect(page.locator('#navbar-user')).toBeVisible();
      if (admin) {
        await expect(page.locator('#manage-groups-link')).toBeVisible();
        await expect(page.locator('#class-grades-link')).toBeVisible();
      }
      await expect(toggler).toHaveAttribute('aria-expanded', 'true');
      // Bootstrap ignores clicks while the collapse animation runs: wait until it is fully open
      const menu = page.locator('#main-navbar-items');
      await expect(menu).toHaveClass(/\bshow\b/);
      await expect(menu).not.toHaveClass(/collapsing/);

      // and closes again
      await toggler.click();
      await expect(page.locator('#sign-out-link'), `${url} closed`).toBeHidden();
      await expect(toggler).toHaveAttribute('aria-expanded', 'false');

      expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), url).toBe(false);
      expect(errors, url).toEqual([]);
    }
  });
});
