import { test, expect, Page } from '@playwright/test';
import { captureStats } from '../../support/dashboard';
import { aliasGroupId, githubToken, skipWithoutToken } from '../../support/env';

/** openspec capability identity-aliases (change associacao-manual-identidades). */
test.describe('Manual association of identities', () => {

  test.skip(!githubToken, skipWithoutToken);

  const ajax = { 'X-Requested-With': 'XMLHttpRequest' };
  const exactly = (text: string) => new RegExp('^' + text.replace(/[.*+?^$()|[\]\\{}]/g, '\\$&') + '$');

  async function openGroup(page: Page) {
    const { body } = await captureStats(page, () => page.goto(`/detail?id=${aliasGroupId}`));
    const admin = (await page.locator('#content').getAttribute('data-admin')) === 'true';
    return { body, admin };
  }

  test('API refuses calls without X-Requested-With (CSRF) and from non-administrators', async ({ page }) => {
    const { admin } = await openGroup(page);
    const form = { groupId: aliasGroupId, identityKey: 'name:e2e-nobody', login: 'nobody' };

    const noHeader = await page.request.post('/identity-aliases', { form });
    expect(noHeader.status()).toBe(403);

    const withHeader = await page.request.post('/identity-aliases', { form, headers: ajax });
    // administrator: "nobody" is not a student (400); anyone else: 403
    expect(withHeader.status()).toBe(admin ? 400 : 403);
  });

  test('non-administrators do not see Link to student / Unlink', async ({ page }) => {
    const { admin } = await openGroup(page);
    test.skip(admin, 'the signed-in user is an administrator (GRADE_CHECKER_ADMINS)');

    await expect(page.locator('.link-identity-button')).toHaveCount(0);
    await expect(page.locator('.unlink-identity-button')).toHaveCount(0);
    await expect(page.locator('#linkIdentityModal')).toHaveCount(0);
  });

  test('administrator links an identity to a student and unlinks it', async ({ page }) => {
    const { body, admin } = await openGroup(page);
    test.skip(!admin, 'the signed-in user is not in GRADE_CHECKER_ADMINS (set E2E_ADMIN_LOGIN / .env.local)');

    const unlinked = body.stats.consolidatedStats.find((s) => s.linked === false && (s as { identityKey?: string }).identityKey);
    test.skip(!unlinked, `group ${aliasGroupId} has no commits without account in All`);

    const key = (unlinked as { identityKey: string }).identityKey;
    const label = unlinked!.author;
    const noAccountRow = page.locator('#contributions-tbody tr', { has: page.locator('.no-account-badge') })
      .filter({ has: page.locator('.author-name', { hasText: exactly(label) }) });

    try {
      // link
      await noAccountRow.locator('.link-identity-button').click();
      await expect(page.locator('#linkIdentityModal')).toBeVisible();
      await expect(page.locator('#link-identity-label')).toHaveText(label);
      const student = await page.locator('#link-identity-login').inputValue();
      expect(student).not.toBe('');

      const { body: afterLink } = await captureStats(page, () => page.locator('#confirm-link-identity').click());

      const studentRow = afterLink.stats.consolidatedStats.find((s) => s.author.toLowerCase() === student.toLowerCase()) as
        { manualIdentities?: Array<{ key: string }> } | undefined;
      expect(studentRow?.manualIdentities?.map((m) => m.key)).toContain(key);
      expect(afterLink.stats.consolidatedStats.some((s) => (s as { identityKey?: string }).identityKey === key)).toBe(false);
      await expect(noAccountRow).toHaveCount(0);
      const manualLine = page.locator('.manual-identity', { has: page.locator(`[data-identity-key="${key}"]`) });
      await expect(manualLine).toContainText('Linked manually:');

      // the association is kept on a new page load
      const { body: reloaded } = await openGroup(page);
      expect(reloaded.stats.consolidatedStats.some((s) => (s as { identityKey?: string }).identityKey === key)).toBe(false);

      // unlink
      page.once('dialog', (dialog) => dialog.accept());
      const { body: afterUnlink } = await captureStats(page, () =>
        page.locator(`.unlink-identity-button[data-identity-key="${key}"]`).click());
      expect(afterUnlink.stats.consolidatedStats.some((s) => (s as { identityKey?: string }).identityKey === key)).toBe(true);
      await expect(noAccountRow).toHaveCount(1);
    } finally {
      // never leave an association behind (the E2E database is persistent now)
      await page.request.delete('/identity-aliases', { params: { groupId: aliasGroupId, identityKey: key }, headers: ajax });
    }
  });
});
