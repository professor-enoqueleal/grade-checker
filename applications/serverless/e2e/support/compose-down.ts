import { execSync } from 'child_process';
import { composeEnv, project, serverlessDir } from './compose';

/** Global teardown: stops the Compose project of the suite and deletes its database (E2E_KEEP=1 keeps it running). */
export default async function composeDown(): Promise<void> {
  if (process.env.E2E_KEEP === '1') {
    console.log(`[e2e] E2E_KEEP=1: the project ${project} keeps running (docker compose -p ${project} down -v to remove it)`);
    return;
  }
  execSync(`docker compose -p ${project} down -v`, { cwd: serverlessDir, env: composeEnv(), stdio: 'inherit' });
}
