/** Shared settings for the E2E tests. */
import fs from 'fs';

export const githubToken = process.env.E2E_GITHUB_TOKEN ?? '';

/** Ids of the groups prepared by tests/auth.setup.ts (db.sql may come without seed groups). */
export const dataFile = `${__dirname}/../.auth/e2e-data.json`;
function prepared(): { groupId?: string; aliasGroupId?: string } {
  try {
    return JSON.parse(fs.readFileSync(dataFile, 'utf-8'));
  } catch {
    return {};
  }
}

/** Group used by the authenticated tests (clinica, which has commits in the three milestones of 2026-2). */
export const groupId = process.env.E2E_GROUP_ID ?? prepared().groupId ?? '3';

export const authFile = `${__dirname}/../.auth/user.json`;

export const skipWithoutToken = 'E2E_GITHUB_TOKEN is not set (run `npm run test:local`, which uses `gh auth token`)';

/** Client id of the instance under test (must match the GITHUB_CLIENT_ID given to the server). */
export const githubClientId = process.env.E2E_GITHUB_CLIENT_ID || 'Iv1.e2e-test-client';

/** Group with commits not linked to an account (default 6 = cannamed), used by the manual association tests. */
export const aliasGroupId = process.env.E2E_ALIAS_GROUP_ID ?? prepared().aliasGroupId ?? '6';

/** Repositories of the groups above, created by the setup when missing. */
export const testGroups = {
  groupId: { name: 'clinica', url: 'https://github.com/Hugodev0711/clinica-pi', owner: 'Hugodev0711', repo: 'clinica-pi' },
  aliasGroupId: { name: 'cannamed', url: 'https://github.com/LucasMoura-dev777/cannamed', owner: 'LucasMoura-dev777', repo: 'cannamed' },
};

/** Logins that are never students (grading.excludedLogins) and are not marked as members by the setup. */
export const excludedLogins = ['professor-enoqueleal'];
