/**
 * Environment of the suite (capability e2e-suite): its own Docker Compose project, isolated from the developer one.
 *   project grade-checker-e2e, SPA on 5183, gateway on 3010, DynamoDB on 8010, database deleted at the end.
 * E2E_BASE_URL: run against an environment that is already running (nothing is started or deleted).
 */
import path from 'path';

export const project = 'grade-checker-e2e';
export const ports = { spa: '5183', gateway: '3010', dynamodb: '8010' };
export const ownEnvironment = !process.env.E2E_BASE_URL;
export const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${ports.spa}`;
export const serverlessDir = path.resolve(__dirname, '../..');

/** Client id given to the environment of the suite (checked by public/github-sign-in.spec.ts). */
export const githubClientId = process.env.E2E_GITHUB_CLIENT_ID || 'Iv1.e2e-test-client';

/**
 * Student run (`npm run test:student`, E2E_STUDENT=1): the suite prepares the groups as administrator, then restarts
 * the API without administrators, so the tests run as a regular user over groups that have students.
 */
export const studentRun = process.env.E2E_STUDENT === '1';

/** Variables of `docker compose`: they win over serverless/.env (the developer configuration is not used). */
export function composeEnv(admins = process.env.E2E_ADMIN_LOGIN ?? ''): NodeJS.ProcessEnv {
  return {
    ...process.env,
    SPA_PORT: ports.spa,
    GATEWAY_PORT: ports.gateway,
    DYNAMODB_PORT: ports.dynamodb,
    GITHUB_CLIENT_ID: githubClientId,
    GITHUB_CLIENT_SECRET: '',
    GRADE_CHECKER_ADMINS: admins,
  };
}
