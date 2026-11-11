/**
 * Mock of the serverless API for `npm run dev:mock`: answers /api/* in memory, with the same routes, bodies, status
 * codes and messages as the backend contract (README.md). Lets the screens run without SAM, DynamoDB or GitHub.
 *
 * Sign in: "Continue with GitHub" signs in as the administrator; a personal access token "student" signs in as a
 * student, "bad" is rejected (401), anything else is the administrator. Data lives until the dev server restarts.
 */
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Plugin } from 'vite';
import * as data from './data';

type Handler = (ctx: Ctx) => unknown | Promise<unknown>;
interface Ctx {
  req: IncomingMessage;
  res: ServerResponse;
  params: Record<string, string>;
  query: URLSearchParams;
  body: Record<string, unknown>;
  login: string | null;
}

class HttpError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
  }
}

const COOKIE = 'gc_mock_session';
const LATENCY_MS = 250;
const sessions = new Map<string, string>();   // session id -> login

const isAdmin = (login: string | null) => login === data.ADMIN_LOGIN;
const me = (login: string) => ({ login, isAdmin: isAdmin(login), avatarUrl: data.avatar(login) });

function requireAdmin(ctx: Ctx, message: string) {
  if (!isAdmin(ctx.login)) throw new HttpError(403, 'FORBIDDEN', message);
}

function group(ctx: Ctx, includeArchived = false) {
  const g = data.findGroup(Number(ctx.params.id), includeArchived);
  if (!g) throw new HttpError(404, 'not_found', 'Group not found.');
  return g;
}

function str(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function signIn(res: ServerResponse, login: string) {
  const id = Math.random().toString(36).slice(2) + Date.now().toString(36);
  sessions.set(id, login);
  res.setHeader('Set-Cookie', `${COOKIE}=${id}; Path=/; HttpOnly; SameSite=Lax`);
}

function summary(g: data.MockGroup) {
  return { id: g.id, name: g.name, repositoryUrl: data.repositoryUrl(g), owner: g.owner, repository: g.repository, members: g.members.length };
}

const LOGIN_PATTERN = /^[a-z\d](?:[a-z\d]|-(?=[a-z\d])){0,38}$/i;
const REPOSITORY_PATTERN = /^https?:\/\/(www\.)?github\.com\/([^/\s]+)\/([^/\s]+?)(\.git)?\/?$/i;

function validatedGroup(ctx: Ctx, editedId: number | null) {
  const name = str(ctx.body.name);
  if (!name) throw new HttpError(400, 'bad_request', 'The group name is required.');
  const match = REPOSITORY_PATTERN.exec(str(ctx.body.repositoryUrl));
  if (!match) throw new HttpError(400, 'bad_request', 'Enter the URL of a GitHub repository (https://github.com/owner/repository).');
  const [owner, repository] = [match[2], match[3]];
  if (repository.includes('does-not-exist')) throw new HttpError(400, 'invalid_repository', 'Repository not found or not accessible with this token.');
  const other = data.groups.find((g) => g.id !== editedId
    && g.owner.toLowerCase() === owner.toLowerCase() && g.repository.toLowerCase() === repository.toLowerCase());
  if (other) {
    throw new HttpError(409, 'already_registered', other.archivedOn
      ? 'This repository is already registered in an archived group. Restore it instead.'
      : 'This repository is already registered.');
  }
  return { name: name.slice(0, 255), owner, repository };
}

const routes: [string, RegExp, Handler, { public?: boolean }?][] = [];
function route(method: string, path: string, handler: Handler, options?: { public?: boolean }) {
  const regex = new RegExp('^' + path.replace(/\{(\w+)\}/g, '(?<$1>[^/]+)') + '$');
  routes.push([method, regex, handler, options]);
}

// ------------------------------------------------------------------------------------------- session

route('GET', '/api/me', (ctx) => {
  if (!ctx.login) throw new HttpError(401, 'unauthorized', 'Sign in to continue.');
  return me(ctx.login);
}, { public: true });

route('POST', '/api/auth/token', (ctx) => {
  const token = str(ctx.body.token);
  if (!token) throw new HttpError(400, 'bad_request', 'Please enter a personal access token.');
  if (token === 'bad') throw new HttpError(401, 'unauthorized', 'GitHub rejected this personal access token.');
  const login = token === 'student' ? data.STUDENT_LOGIN : data.ADMIN_LOGIN;
  signIn(ctx.res, login);
  return me(login);
}, { public: true });

route('GET', '/api/auth/github', (ctx) => {
  ctx.res.statusCode = 302;
  ctx.res.setHeader('Location', '/api/auth/callback?code=mock&state=mock');
  return undefined;
}, { public: true });

route('GET', '/api/auth/callback', (ctx) => {
  signIn(ctx.res, data.ADMIN_LOGIN);
  ctx.res.statusCode = 302;
  ctx.res.setHeader('Location', '/home');
  return undefined;
}, { public: true });

route('POST', '/api/auth/logout', (ctx) => {
  const id = cookie(ctx.req);
  if (id) sessions.delete(id);
  ctx.res.setHeader('Set-Cookie', `${COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`);
  return { signedOut: true };
}, { public: true });

// ------------------------------------------------------------------------------------------- authenticated

route('GET', '/api/config', () => data.config());

route('GET', '/api/groups', () => data.groups.filter((g) => g.archivedOn === null).map(summary));

route('GET', '/api/groups/archived', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage groups.');
  return { groups: data.groups.filter((g) => g.archivedOn !== null).map((g) => ({ ...summary(g), archivedOn: g.archivedOn })) };
});

route('GET', '/api/groups/{id}', (ctx) => {
  const g = group(ctx);
  return {
    group: summary(g),
    members: g.members,
    cards: data.cards(g),
    contributorsCount: data.contributorsCount(g),
    contributorsError: null,
    aliasTargets: isAdmin(ctx.login) ? g.members.map((m) => m.login).sort() : [],
  };
});

route('GET', '/api/groups/{id}/contributions', (ctx) => {
  const g = group(ctx);
  const period = ctx.query.get('period') || 'all';
  if (period === 'custom' && (!ctx.query.get('since') || !ctx.query.get('until'))) {
    throw new HttpError(400, 'invalid_period', 'Fill in both dates of the custom period.');
  }
  return data.report(g, period, ctx.query.get('since') ?? undefined, ctx.query.get('until') ?? undefined);
});

route('GET', '/api/groups/{id}/commits', (ctx) => {
  const g = group(ctx);
  const author = str(ctx.query.get('author'));
  const period = ctx.query.get('period') || 'all';
  const known = ['m1', 'm2', 'm3', 'all', 'custom'].includes(period);
  const used = known ? period : 'all';
  const { rows, alsoCounted, activity } = data.commits(g, author, used);
  const m = data.MILESTONES.find((x) => x.key === used);
  return {
    author, period: used,
    periodText: used === 'all' ? 'All history' : m ? `${m.startText} – ${m.endText}` : `${ctx.query.get('since')} – ${ctx.query.get('until')}`,
    ...data.bounds(used, ctx.query.get('since'), ctx.query.get('until')),
    periodWarning: known ? null : `Unknown period '${period}'. Showing all history instead.`,
    customSince: ctx.query.get('since') ?? '', customUntil: ctx.query.get('until') ?? data.config().customUntil,
    periods: data.periods(), alsoCounted, truncated: false, commits: rows, activity,
  };
});

// ------------------------------------------------------------------------------------------- administrators: groups

route('POST', '/api/groups', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage groups.');
  const v = validatedGroup(ctx, null);
  const g: data.MockGroup = { id: Math.max(0, ...data.groups.map((x) => x.id)) + 1, ...v, archivedOn: null, members: [], aliases: new Map() };
  data.groups.push(g);
  return summary(g);
});

route('PUT', '/api/groups/{id}', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage groups.');
  const g = group(ctx);
  Object.assign(g, validatedGroup(ctx, g.id));
  return summary(g);
});

route('POST', '/api/groups/{id}/archive', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage groups.');
  const g = group(ctx);
  g.archivedOn = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'America/Sao_Paulo' });
  return summary(g);
});

route('POST', '/api/groups/{id}/restore', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage groups.');
  const g = group(ctx, true);
  if (!g.archivedOn) throw new HttpError(404, 'not_found', 'Group not found.');
  g.archivedOn = null;
  return summary(g);
});

// ------------------------------------------------------------------------------------------- administrators: members

route('GET', '/api/groups/{id}/members', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage members.');
  const g = group(ctx);
  return { group: { id: g.id, name: g.name }, members: [...g.members].sort((a, b) => a.login.toLowerCase().localeCompare(b.login.toLowerCase())) };
});

route('POST', '/api/groups/{id}/members', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage members.');
  const g = group(ctx);
  const login = str(ctx.body.login);
  if (!LOGIN_PATTERN.test(login)) throw new HttpError(400, 'bad_request', 'A valid GitHub login is required.');
  if (login.includes('ghost')) throw new HttpError(400, 'invalid_login', 'This GitHub user does not exist.');
  if (g.members.some((m) => m.login.toLowerCase() === login.toLowerCase())) {
    throw new HttpError(409, 'already_member', 'This user is already a member of the group.');
  }
  const member = { login, name: str(ctx.body.name) || login };
  g.members.push(member);
  return member;
});

route('PUT', '/api/groups/{id}/members/{login}', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage members.');
  const g = group(ctx);
  const current = decodeURIComponent(ctx.params.login);
  const member = g.members.find((m) => m.login.toLowerCase() === current.toLowerCase());
  if (!member) throw new HttpError(404, 'not_found', 'This user is not a member of the group.');
  const login = str(ctx.body.login);
  if (!LOGIN_PATTERN.test(login)) throw new HttpError(400, 'invalid_login', 'This GitHub user does not exist.');
  if (login.toLowerCase() !== current.toLowerCase() && g.members.some((m) => m.login.toLowerCase() === login.toLowerCase())) {
    throw new HttpError(409, 'already_member', 'This user is already a member of the group.');
  }
  g.aliases.forEach((value, key) => { if (value.toLowerCase() === current.toLowerCase()) g.aliases.set(key, login); });
  member.login = login;
  member.name = str(ctx.body.name) || login;
  return member;
});

route('DELETE', '/api/groups/{id}/members/{login}', (ctx) => {
  requireAdmin(ctx, 'Only administrators can manage members.');
  const g = group(ctx);
  const login = decodeURIComponent(ctx.params.login).toLowerCase();
  const index = g.members.findIndex((m) => m.login.toLowerCase() === login);
  if (index < 0) throw new HttpError(404, 'not_found', 'This user is not a member of the group.');
  if ([...g.aliases.values()].some((v) => v.toLowerCase() === login)) {
    throw new HttpError(409, 'has_aliases', 'Unlink the identities linked to this student before removing them.');
  }
  g.members.splice(index, 1);
  return { removed: true };
});

// ------------------------------------------------------------------------------------------- administrators: aliases

route('POST', '/api/groups/{id}/aliases', (ctx) => {
  requireAdmin(ctx, 'Only administrators can link identities.');
  const g = group(ctx);
  const login = str(ctx.body.login);
  const key = str(ctx.body.identityKey);
  if (!/^(email|name):.+/.test(key)) throw new HttpError(400, 'invalid_identity', 'Invalid identity key.');
  if (!g.members.some((m) => m.login.toLowerCase() === login.toLowerCase())) {
    throw new HttpError(400, 'invalid_login', 'The login must be a student of this group.');
  }
  g.aliases.set(key, login);
  return { groupId: g.id, identityKey: key, login };
});

route('DELETE', '/api/groups/{id}/aliases', (ctx) => {
  requireAdmin(ctx, 'Only administrators can link identities.');
  const g = group(ctx);
  if (!g.aliases.delete(str(ctx.query.get('identityKey')))) throw new HttpError(404, 'not_found', 'This identity is not linked.');
  return { removed: true };
});

// ------------------------------------------------------------------------------------------- administrators: grades

route('GET', '/api/grades', (ctx) => {
  requireAdmin(ctx, 'Only administrators can see class grades.');
  return {
    groups: data.groups.filter((g) => g.archivedOn === null)
      .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.id - b.id)
      .map((g) => ({ id: g.id, name: g.name, students: g.members.map((m) => m.login).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase())) })),
  };
});

route('GET', '/api/grades/{id}', (ctx) => {
  requireAdmin(ctx, 'Only administrators can see class grades.');
  return data.groupGrades(group(ctx));
});

// ------------------------------------------------------------------------------------------- plumbing

function cookie(req: IncomingMessage): string | null {
  const match = (req.headers.cookie ?? '').match(new RegExp(`(?:^|;\\s*)${COOKIE}=([^;]+)`));
  return match ? match[1] : null;
}

async function readBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    return {};
  }
}

function send(res: ServerResponse, status: number, body: unknown) {
  if (res.statusCode === 302) {
    res.end();
    return;
  }
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.end(body === undefined ? '' : JSON.stringify(body));
}

export function mockApi(): Plugin {
  return {
    name: 'grade-checker-mock-api',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url ?? '/', 'http://localhost');
        if (!url.pathname.startsWith('/api/')) return next();
        await new Promise((r) => setTimeout(r, LATENCY_MS));

        const found = routes.find(([method, regex]) => method === req.method && regex.test(url.pathname));
        if (!found) return send(res, 404, { error: 'not_found', message: 'Not found.' });
        const [method, regex, handler, options] = found;

        const id = cookie(req);
        const login = id ? sessions.get(id) ?? null : null;
        try {
          if (!options?.public && !login) throw new HttpError(401, 'unauthorized', 'Sign in to continue.');
          // mutations must come from the page (CSRF protection of the real backend)
          if (method !== 'GET' && !options?.public && req.headers['x-requested-with'] !== 'XMLHttpRequest') {
            throw new HttpError(403, 'forbidden', 'Missing X-Requested-With header.');
          }
          const body = method === 'GET' ? {} : await readBody(req);
          const result = await handler({ req, res, params: regex.exec(url.pathname)!.groups ?? {}, query: url.searchParams, body, login });
          send(res, 200, result);
        } catch (e) {
          if (e instanceof HttpError) send(res, e.status, { error: e.code, message: e.message });
          else {
            server.config.logger.error(String(e));
            send(res, 500, { error: 'error', message: 'Unexpected error.' });
          }
        }
      });
    },
  };
}
