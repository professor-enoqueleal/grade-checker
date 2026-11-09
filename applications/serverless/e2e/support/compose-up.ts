import { execSync } from 'child_process';
import { baseURL, composeEnv, project, serverlessDir, studentRun } from './compose';
import { excludedLogins, testGroups } from './env';

/**
 * Global setup: builds and starts the Compose project of the suite and waits until the API answers.
 * Student run: prepares the test groups as administrator, then restarts only the API without administrators.
 */
export default async function composeUp(): Promise<void> {
  if (studentRun && (!process.env.E2E_GITHUB_TOKEN || !process.env.E2E_ADMIN_LOGIN)) {
    // checked before starting anything: without them the groups would have no students
    throw new Error('student run needs E2E_GITHUB_TOKEN and E2E_ADMIN_LOGIN (`gh auth token` / `gh api user -q .login` failed?)');
  }
  console.log(`[e2e] docker compose -p ${project} up --build -d (first run: a few minutes)`);
  execSync(`docker compose -p ${project} up --build -d`, { cwd: serverlessDir, env: composeEnv(), stdio: 'inherit' });
  await waitForApi();

  if (studentRun) {
    await prepareAsAdministrator(process.env.E2E_GITHUB_TOKEN);
    console.log('[e2e] student run: restarting the API without administrators');
    execSync(`docker compose -p ${project} up -d api`, { cwd: serverlessDir, env: composeEnv(''), stdio: 'inherit' });
    await waitForApi();
  }
}

async function waitForApi() {
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseURL}/api/me`);
      if (response.status === 401) return;   // API up, no session
    } catch {
      // not yet
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`the environment of the suite did not answer at ${baseURL}/api/me`);
}

/** Marks the contributors of the test groups as members (same rule as tests/auth.setup.ts), through the API. */
async function prepareAsAdministrator(token: string) {
  const headers = { 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/json' };
  const signIn = await fetch(`${baseURL}/api/auth/token`, { method: 'POST', headers, body: JSON.stringify({ token }) });
  if (!signIn.ok) throw new Error(`student run: sign-in for the preparation failed (${signIn.status})`);
  const cookie = (signIn.headers.get('set-cookie') ?? '').split(';')[0];
  const call = (path: string, init: RequestInit = {}) => fetch(`${baseURL}${path}`, { ...init, headers: { ...headers, Cookie: cookie } });

  const groups = (await (await call('/api/groups')).json()) as Array<{ id: number; name: string }>;
  for (const g of Object.values(testGroups)) {
    const group = groups.find((x) => x.name === g.name);
    if (!group) throw new Error(`student run: group ${g.name} is missing (the seed creates it)`);
    const detail = (await (await call(`/api/groups/${group.id}`)).json()) as { cards: Array<{ login: string; member: boolean }> };
    for (const card of detail.cards) {
      if (card.member || card.login.endsWith('[bot]') || excludedLogins.includes(card.login.toLowerCase())) continue;
      const added = await call(`/api/groups/${group.id}/members`, { method: 'POST', body: JSON.stringify({ login: card.login, manual: false }) });
      if (added.status !== 200 && added.status !== 409) throw new Error(`student run: mark ${card.login}: ${added.status}`);
    }
  }
  await call('/api/auth/logout', { method: 'POST' });
  console.log('[e2e] student run: test groups prepared as administrator');
}
