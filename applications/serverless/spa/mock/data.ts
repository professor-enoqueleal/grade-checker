/**
 * Fictitious data of the mock API (npm run dev:mock): groups, members, contributions and commits shaped like the
 * real backend answers. Nothing here talks to GitHub. Semester 2026-2 milestones, as in milestones.properties.
 */
import type {
  AppConfig, CommitActivity, CommitRow, ContributionRow, ContributionsReport, GradeDetails, GradingExplanation,
  GroupCard, PeriodOption,
} from '../src/api/types';

export interface MockGroup {
  id: number;
  name: string;
  owner: string;
  repository: string;
  archivedOn: string | null;
  members: { login: string; name: string }[];
  /** identity key -> login */
  aliases: Map<string, string>;
}

/** Authors of each repository: login (null = commits without a GitHub account), lines and commits per milestone. */
interface MockAuthor {
  login: string | null;
  name: string;
  email: string;
  commits: [number, number, number];
  additions: [number, number, number];
  deletions: [number, number, number];
}

export const ADMIN_LOGIN = 'professor';
export const STUDENT_LOGIN = 'student-demo';

export const MILESTONES = [
  { key: 'm1', label: 'Milestone 1', start: '2026-08-13', end: '2026-09-17', startText: 'Aug 13, 2026', endText: 'Sep 17, 2026', pt: ['13/08/2026', '17/09/2026'] },
  { key: 'm2', label: 'Milestone 2', start: '2026-09-18', end: '2026-10-29', startText: 'Sep 18, 2026', endText: 'Oct 29, 2026', pt: ['18/09/2026', '29/10/2026'] },
  { key: 'm3', label: 'Milestone 3', start: '2026-10-30', end: '2026-11-19', startText: 'Oct 30, 2026', endText: 'Nov 19, 2026', pt: ['30/10/2026', '19/11/2026'] },
];

const today = () => new Date().toISOString().slice(0, 10);

export function periods(): PeriodOption[] {
  const now = today();
  return [
    ...MILESTONES.map((m) => (m.start > now
      ? { key: m.key, label: m.label, enabled: false, hint: `Starts on ${m.startText}` }
      : { key: m.key, label: m.label, enabled: true, hint: `${m.startText} – ${m.endText}` })),
    { key: 'all', label: 'All', enabled: true, hint: 'All history' },
  ];
}

const inProgress = (key: string) => {
  const m = MILESTONES.find((x) => x.key === key)!;
  const now = today();
  return m.start <= now && now <= m.end;
};
const started = (key: string) => MILESTONES.find((x) => x.key === key)!.start <= today();

export const gradingExplanation: GradingExplanation = {
  milestones: MILESTONES.map((m) => ({ label: m.label, start: m.pt[0], end: m.pt[1] })),
  maxPoints: '3',
  totalPoints: '9',
  weightEffectiveLines: '50%',
  weightActiveDays: '30%',
  weightRelevantCommits: '20%',
  lineCap: 400,
  reorganizationMinFiles: 30,
  reorganizationBalance: '60%',
  reorganizationFactor: '10%',
  relevantCommitMinLines: 20,
  tolerance: '80%',
  timeZone: 'horário de Brasília',
  excludesLogins: true,
  exampleStudents: 4,
  exampleFairShare: '25%',
  exampleFullShare: '20%',
  exampleFullGrade: '3,00',
  exampleLowShare: '10%',
  exampleLowGrade: '1,50',
};

export function config(): AppConfig {
  return { periods: periods(), customUntil: today() + 'T23:59', gradingExplanation };
}

// ------------------------------------------------------------------------------------------- groups

export const groups: MockGroup[] = [
  {
    id: 1, name: 'projeto-chamados', owner: 'Saacbs', repository: 'Projeto-Chamados', archivedOn: null,
    members: [
      { login: 'ana-souza', name: 'Ana Souza' },
      { login: 'bruno-lima', name: 'Bruno Lima' },
      { login: 'carla-mendes', name: 'Carla Mendes' },
      { login: 'diego-rocha', name: 'Diego Rocha' },
    ],
    aliases: new Map([['email:carla.mendes@senac.example', 'carla-mendes']]),
  },
  {
    id: 2, name: 'sensei-dev', owner: 'VitorConrado04', repository: 'sensei-dev', archivedOn: null,
    members: [
      { login: 'vitor-conrado', name: 'Vitor Conrado' },
      { login: 'julia-alves', name: 'Julia Alves' },
      { login: 'marcos-pereira', name: 'Marcos Pereira' },
    ],
    aliases: new Map(),
  },
  {
    id: 3, name: 'clinica', owner: 'Hugodev0711', repository: 'clinica-pi', archivedOn: null,
    members: [],
    aliases: new Map(),
  },
  {
    id: 4, name: 'nonna-pizza', owner: 'rulckus', repository: 'nonna-pizza', archivedOn: 'Oct 2, 2026',
    members: [{ login: 'rafael-ulckus', name: 'Rafael Ulckus' }],
    aliases: new Map(),
  },
];

export const repositoryUrl = (g: MockGroup) => `https://github.com/${g.owner}/${g.repository}`;

const authors: Record<number, MockAuthor[]> = {
  1: [
    { login: 'ana-souza', name: 'Ana Souza', email: 'ana@users.noreply.github.com', commits: [14, 18, 6], additions: [1820, 2410, 640], deletions: [310, 520, 120] },
    { login: 'bruno-lima', name: 'Bruno Lima', email: 'bruno@users.noreply.github.com', commits: [9, 11, 3], additions: [960, 1350, 210], deletions: [140, 260, 40] },
    { login: 'carla-mendes', name: 'Carla Mendes', email: 'carla@users.noreply.github.com', commits: [6, 8, 4], additions: [540, 880, 300], deletions: [90, 150, 60] },
    { login: null, name: 'Carla M', email: 'carla.mendes@senac.example', commits: [3, 2, 0], additions: [220, 140, 0], deletions: [30, 20, 0] },
    { login: 'diego-rocha', name: 'Diego Rocha', email: 'diego@users.noreply.github.com', commits: [2, 1, 0], additions: [120, 40, 0], deletions: [10, 5, 0] },
    { login: null, name: 'PAULO HENRIQUE DE MORAES DORNELAS', email: 'paulo@localhost', commits: [1, 3, 1], additions: [80, 260, 30], deletions: [5, 40, 2] },
  ],
  2: [
    { login: 'vitor-conrado', name: 'Vitor Conrado', email: 'vitor@users.noreply.github.com', commits: [20, 15, 7], additions: [3100, 2200, 900], deletions: [600, 410, 150] },
    { login: 'julia-alves', name: 'Julia Alves', email: 'julia@users.noreply.github.com', commits: [12, 14, 5], additions: [1500, 1700, 520], deletions: [200, 330, 80] },
    { login: 'marcos-pereira', name: 'Marcos Pereira', email: 'marcos@users.noreply.github.com', commits: [10, 9, 6], additions: [1300, 1100, 610], deletions: [180, 150, 90] },
  ],
  3: [
    { login: 'hugodev0711', name: 'Hugo', email: 'hugo@users.noreply.github.com', commits: [8, 12, 4], additions: [700, 1500, 420], deletions: [60, 300, 70] },
  ],
  4: [],
};

export function findGroup(id: number, includeArchived = false): MockGroup | undefined {
  return groups.find((g) => g.id === id && (includeArchived || g.archivedOn === null));
}

export function cards(g: MockGroup): GroupCard[] {
  const memberLogins = new Set(g.members.map((m) => m.login.toLowerCase()));
  const contributors = (authors[g.id] ?? []).filter((a) => a.login !== null);
  const result: GroupCard[] = contributors.map((a) => ({
    login: a.login!,
    avatarUrl: avatar(a.login!),
    htmlUrl: `https://github.com/${a.login}`,
    contributions: a.commits[0] + a.commits[1] + a.commits[2],
    member: memberLogins.has(a.login!.toLowerCase()),
  }));
  const seen = new Set(result.map((c) => c.login.toLowerCase()));
  g.members.filter((m) => !seen.has(m.login.toLowerCase()))
    .sort((a, b) => a.login.toLowerCase().localeCompare(b.login.toLowerCase()))
    .forEach((m) => result.push({
      login: m.login, avatarUrl: avatar(m.login), htmlUrl: `https://github.com/${m.login}`, contributions: 0, member: true,
    }));
  return result;
}

/** Local avatar (SVG with the initials): the mock never calls github.com. */
function avatar(login: string): string {
  const hue = Math.abs(hash(login)) % 360;
  const initials = login.split('-').map((p) => p[0] ?? '').join('').slice(0, 2).toUpperCase();
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="hsl(${hue},45%,80%)"/>`
    + `<text x="50" y="62" font-family="sans-serif" font-size="34" text-anchor="middle" fill="hsl(${hue},45%,25%)">${initials}</text></svg>`;
  return 'data:image/svg+xml,' + encodeURIComponent(svg);
}

export const contributorsCount = (g: MockGroup) => (authors[g.id] ?? []).filter((a) => a.login !== null).length;

// ------------------------------------------------------------------------------------------- dashboard

const identityKey = (a: MockAuthor) => `email:${a.email}`;

/** Milestone indexes counted by a period. */
function indexesOf(period: string): number[] {
  if (period === 'm1') return [0];
  if (period === 'm2') return [1];
  if (period === 'm3') return [2];
  return [0, 1, 2];
}

interface Consolidated {
  author: string;
  linked: boolean;
  identities: string[];
  identityKey: string | null;
  manual: { key: string; identity: string }[];
  commits: number;
  additions: number;
  deletions: number;
}

/** Attribution as in AuthorAggregator: login; manual alias (commits without account); otherwise author without account. */
function consolidate(g: MockGroup, idx: number[]): Consolidated[] {
  const byAuthor = new Map<string, Consolidated>();
  const sum = (t: [number, number, number]) => idx.reduce((s, i) => s + t[i], 0);
  for (const a of authors[g.id] ?? []) {
    if (sum(a.commits) === 0) continue;
    const aliasLogin = a.login === null ? g.aliases.get(identityKey(a)) : undefined;
    const login = a.login ?? aliasLogin ?? null;
    const key = login ? login.toLowerCase() : `unlinked:${a.email}`;
    const row = byAuthor.get(key) ?? {
      author: login ?? a.name, linked: login !== null, identities: [], identityKey: login ? null : identityKey(a), manual: [],
      commits: 0, additions: 0, deletions: 0,
    };
    if (a.login === null) {
      row.identities.push(a.email);
      if (aliasLogin) row.manual.push({ key: identityKey(a), identity: a.email });
    }
    row.commits += sum(a.commits);
    row.additions += sum(a.additions);
    row.deletions += sum(a.deletions);
    byAuthor.set(key, row);
  }
  return [...byAuthor.values()].sort((x, y) => (y.additions + y.deletions) - (x.additions + x.deletions));
}

/** Simplified grade (same shape as GradeCalculator): share of lines of the students, tolerance 0.8, max 3. */
function grades(g: MockGroup, rows: Consolidated[]): Map<string, { grade: number; details: GradeDetails }> {
  const students = g.members.map((m) => m.login.toLowerCase());
  const result = new Map<string, { grade: number; details: GradeDetails }>();
  if (students.length === 0) return result;
  const lines = (r?: Consolidated) => (r ? r.additions + r.deletions : 0);
  const byLogin = new Map(rows.filter((r) => r.linked).map((r) => [r.author.toLowerCase(), r]));
  const total = students.reduce((s, l) => s + lines(byLogin.get(l)), 0) || 1;
  const fair = 1 / students.length;
  for (const login of students) {
    const r = byLogin.get(login);
    const share = lines(r) / total;
    const ratio = Math.round((share / fair) * 100) / 100;
    const grade = Math.round(3 * Math.min(1, ratio / 0.8) * 100) / 100;
    result.set(login, {
      grade,
      details: { effectiveLines: lines(r), activeDays: Math.min(30, r ? r.commits : 0), relevantCommits: r ? Math.ceil(r.commits * 0.6) : 0, indexShare: share, ratio },
    });
  }
  return result;
}

export function report(g: MockGroup, period: string, since?: string, until?: string): ContributionsReport {
  const idx = period === 'custom' ? [0, 1, 2] : indexesOf(period);
  const rows = consolidate(g, idx);
  const totalLines = rows.reduce((s, r) => s + r.additions + r.deletions, 0) || 1;
  const out: ContributionRow[] = rows.map((r) => ({
    author: r.author, commits: r.commits, totalAdditions: r.additions, totalDeletions: r.deletions,
    percentage: Math.round(((r.additions + r.deletions) / totalLines) * 10000) / 100,
    linked: r.linked, gitIdentities: r.identities, identityKey: r.identityKey, manualIdentities: r.manual,
  }));
  // students without commits still appear (zeros), so that a 0.00 grade is visible
  const present = new Set(out.map((r) => r.author.toLowerCase()));
  if (period !== 'custom') {
    g.members.filter((m) => !present.has(m.login.toLowerCase())).forEach((m) => out.push({
      author: m.login, commits: 0, totalAdditions: 0, totalDeletions: 0, percentage: 0, linked: true,
      gitIdentities: [], identityKey: null, manualIdentities: [],
    }));
  }

  let grading: ContributionsReport['grading'] = null;
  if (period.startsWith('m')) {
    const result = grades(g, rows);
    out.forEach((r) => {
      const x = result.get(r.author.toLowerCase());
      r.grade = x ? x.grade : null;
      r.gradeDetails = x ? x.details : null;
    });
    const excluded = rows.filter((r) => !r.linked).reduce((s, r) => s + r.commits, 0);
    grading = { maxPoints: 3, tolerance: 0.8, fairShare: g.members.length ? 1 / g.members.length : 0, students: g.members.length,
      excludedCommits: excluded, excludedLines: 0, provisional: inProgress(period) };
  } else if (period === 'all') {
    const perMilestone = MILESTONES.map((m, i) => (started(m.key) ? grades(g, consolidate(g, [i])) : null));
    out.forEach((r) => {
      const login = r.author.toLowerCase();
      if (!g.members.some((m) => m.login.toLowerCase() === login)) {
        r.milestoneGrades = null;
        r.total = null;
        return;
      }
      const values: Record<string, number | null> = {};
      let total = 0;
      MILESTONES.forEach((m, i) => {
        const value = perMilestone[i]?.get(login)?.grade ?? null;
        values[m.key] = value;
        total += value ?? 0;
      });
      r.milestoneGrades = values;
      r.total = Math.round(total * 100) / 100;
    });
    grading = { students: g.members.length, provisionalMilestones: MILESTONES.filter((m) => inProgress(m.key)).map((m) => m.key) };
  }

  const m = MILESTONES.find((x) => x.key === period);
  const periodText = period === 'all' ? 'All history'
    : m ? `${m.startText} – ${m.endText}`
      : `${(since ?? '').replace('T', ' ')} – ${(until ?? '').replace('T', ' ')}`;
  return {
    period, periodText,
    totalCommits: rows.reduce((s, r) => s + r.commits, 0),
    truncated: false,
    grading,
    rows: out,
  };
}

// ------------------------------------------------------------------------------------------- commit history

const MESSAGES = [
  'Add ticket list page with filters by status and priority',
  'Fix NullPointerException when the ticket has no assignee',
  'Refactor TicketService: extract validation and notification logic into dedicated classes for clarity',
  'Update README with setup instructions',
  'Create login screen',
  'Add unit tests for the priority rules',
  'Style the dashboard cards',
  'Merge-free rebase of the report feature',
];

export function commits(g: MockGroup, author: string, period: string): { rows: CommitRow[]; alsoCounted: string[]; activity: CommitActivity | null } {
  const idx = period === 'custom' ? [0, 1, 2] : indexesOf(period);
  const own = (authors[g.id] ?? []).filter((a) => (a.login ?? g.aliases.get(identityKey(a)) ?? '').toLowerCase() === author.toLowerCase());
  const rows: CommitRow[] = [];
  const dates: Date[] = [];
  const alsoCounted: string[] = [];
  own.forEach((a, ai) => {
    if (a.login === null) alsoCounted.push(a.email + ' (manual)');
    idx.forEach((i) => {
      const m = MILESTONES[i];
      const start = new Date(m.start + 'T12:00:00Z').getTime();
      const end = Math.min(new Date(m.end + 'T12:00:00Z').getTime(), Date.now());
      for (let c = 0; c < a.commits[i]; c++) {
        const t = new Date(start + ((c * 7 + ai * 3 + i) % 37) / 37 * Math.max(0, end - start));
        const seed = `${g.id}:${a.email}:${i}:${c}`;
        const sha = [0, 1, 2, 3, 4].map((k) => (hash(seed + k) >>> 0).toString(16).padStart(8, '0')).join('');
        dates.push(t);
        rows.push({
          day: t.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'America/Sao_Paulo' }),
          time: t.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo' }),
          message: MESSAGES[(c + i + ai) % MESSAGES.length],
          sha,
          url: `${repositoryUrl(g)}/commit/${sha}`,
          identity: a.login === null ? a.email : null,
          manual: a.login === null,
        });
      }
    });
  });
  const order = rows.map((r, i) => ({ r, t: dates[i].getTime() })).sort((x, y) => y.t - x.t);
  return { rows: order.map((x) => x.r), alsoCounted, activity: activity(order.map((x) => new Date(x.t))) };
}

/** Weekly columns, one series per milestone (as CommitActivity). */
function activity(dates: Date[]): CommitActivity | null {
  if (dates.length === 0) return null;
  const monday = (d: Date) => {
    const x = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
    x.setUTCDate(x.getUTCDate() - ((x.getUTCDay() + 6) % 7));
    return x;
  };
  const first = monday(new Date(Math.min(...dates.map((d) => d.getTime()))));
  const last = monday(new Date(Math.max(...dates.map((d) => d.getTime()))));
  const weeks = Math.round((last.getTime() - first.getTime()) / (7 * 86400000)) + 1;
  const labels: string[] = [];
  const tooltips: string[] = [];
  for (let w = 0; w < weeks; w++) {
    const d = new Date(first.getTime() + w * 7 * 86400000);
    labels.push(d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' }));
    tooltips.push('Week of ' + d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }));
  }
  const series = MILESTONES.map((m) => ({ key: m.key, label: m.label, counts: new Array(weeks).fill(0) as number[] }));
  dates.forEach((d) => {
    const day = d.toISOString().slice(0, 10);
    const s = series[Math.max(0, MILESTONES.findIndex((m) => m.start <= day && day <= m.end))];
    s.counts[Math.round((monday(d).getTime() - first.getTime()) / (7 * 86400000))]++;
  });
  const used = series.filter((s) => s.counts.some((c) => c > 0));
  const activeDays = new Set(dates.map((d) => d.toISOString().slice(0, 10))).size;
  return {
    granularity: 'week',
    title: 'Commits per week',
    summary: `${dates.length} commits on ${activeDays} active days`,
    ariaLabel: `Commits per week: ${dates.length} commits on ${activeDays} active days`,
    labels, tooltips, series: used,
  };
}

/** FNV-1a with a final mix: distinct-looking fake hashes for similar inputs. */
function hash(text: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 0x01000193);
  h ^= h >>> 15;
  h = Math.imul(h, 0x2c1b3c6d);
  return h ^ (h >>> 12);
}

/** Grades of the period All for /api/grades/{id}. */
export function groupGrades(g: MockGroup) {
  const r = report(g, 'all');
  const students = g.members.map((m) => {
    const row = r.rows.find((x) => x.author.toLowerCase() === m.login.toLowerCase());
    return { login: m.login, m1: row?.milestoneGrades?.m1 ?? null, m2: row?.milestoneGrades?.m2 ?? null, m3: row?.milestoneGrades?.m3 ?? null, total: row?.total ?? null };
  });
  const excluded = consolidate(g, [0, 1, 2]).filter((x) => !x.linked).reduce((s, x) => s + x.commits, 0);
  return { groupId: g.id, students, provisionalMilestones: r.grading?.provisionalMilestones ?? [], excludedCommits: excluded };
}
