import { expect, Page } from '@playwright/test';

/** Opens the user menu of the navbar (avatar and login, top right): "Signed in as", the role and "Sign out" are inside it. */
export async function openUserMenu(page: Page) {
  await page.locator('#main-navbar #user-menu').click();
  await expect(page.locator('#user-menu-items')).toBeVisible();
}
