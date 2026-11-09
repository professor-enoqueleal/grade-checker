import { test, expect, Page } from '@playwright/test';
import { githubToken, groupId, skipWithoutToken } from '../../support/env';

/** Capability navigation-bar of the SPA. Never clicks "Sign out": the session is shared. */
test.describe('Navigation bar', () => {

  test.skip(!githubToken, skipWithoutToken);

  async function context(page: Page) {
    await page.goto(`/detail?id=${groupId}`);
    const admin = (await page.locator('#content').getAttribute('data-admin')) === 'true';
    const author = (await page.locator('.card[data-login]').first().getAttribute('data-login'))!;
    return { admin, author };
  }

  test('every authenticated page has the same navbar, with the breadcrumb below it', async ({ page }) => {
    const { admin, author } = await context(page);
    const pages: Array<[string, string | null]> = [
      ['/home', null],
      [`/detail?id=${groupId}`, null],
      [`/contributions-detail?${new URLSearchParams({ groupId, author })}`, null],
      [`/group/members?id=${groupId}`, null],
      ['/grades', 'class-grades-link'],
      ['/groups', 'manage-groups-link'],
    ];

    for (const [url, active] of pages) {
      await page.goto(url);
      const navbar = page.locator('#main-navbar');
      await expect(navbar, url).toBeVisible();
      await expect(page.locator('#navbar-user')).not.toBeEmpty();
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
      await expect(navbar.locator('#sign-out-link')).toHaveText('Sign out');   // POST /api/auth/logout on click

      // the breadcrumb is still there, below the navbar
      const nav = (await navbar.boundingBox())!;
      const crumb = (await page.locator('nav[aria-label="breadcrumb"]').boundingBox())!;
      expect(crumb.y, url).toBeGreaterThanOrEqual(nav.y + nav.height);

      // Bootstrap JS is bundled once in the application (no extra copy from a CDN or webjar: two copies break the
      // phone menu, which opens but never closes; the phone test below checks the menu opens and closes)
      expect(await page.locator('script[src*="bootstrap"]').count(), url).toBe(0);
    }
  });

  test('the brand goes to the home', async ({ page }) => {
    await page.goto('/grades');
    await page.locator('#navbar-brand').click();
    await expect(page).toHaveURL(/\/home$/);
    await expect(page.locator('.breadcrumb')).toHaveText('Home');
  });

  test('on a phone the menu button opens and closes the links on every page, without script errors', async ({ page }) => {
    const { admin, author } = await context(page);
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    await page.setViewportSize({ width: 360, height: 800 });

    for (const url of ['/home', `/detail?id=${groupId}`, `/contributions-detail?${new URLSearchParams({ groupId, author })}`,
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
