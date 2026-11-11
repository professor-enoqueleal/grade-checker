/**
 * Contract of the serverless API (/api/*), shared by the screens and the mock.
 * Documented in spa/README.md ("Contrato da API"). Dates and period texts come formatted by the
 * backend, in the time zone of milestones.properties, as the JSP pages do today.
 */

/** Error body of every endpoint: { error, message }. */
export interface ApiErrorBody {
  error: string;
  message: string;
}

/** GET /api/me */
export interface Me {
  login: string;
  isAdmin: boolean;
  /** GitHub avatar (https://github.com/<login>.png?size=64); absent in older responses */
  avatarUrl?: string;
}

/** Period button (Milestone 1/2/3 and All). */
export interface PeriodOption {
  key: string;          // m1, m2, m3, all
  label: string;        // "Milestone 1", "All"
  enabled: boolean;     // milestones only after they start
  hint: string;         // "Aug 13, 2026 – Sep 17, 2026", "Starts on …", "Not configured"
  /** raw dates of the milestone (yyyy-MM-dd), null for All or a milestone not configured; the SPA writes the texts */
  start?: string | null;
  end?: string | null;
}

/** Numbers of the pt-BR box "Como as notas são calculadas" (grading.properties + milestones.properties), already formatted. */
export interface GradingExplanation {
  milestones: { label: string; start: string; end: string }[];
  maxPoints: string;
  totalPoints: string;
  weightEffectiveLines: string;
  weightActiveDays: string;
  weightRelevantCommits: string;
  lineCap: number;
  reorganizationMinFiles: number;
  reorganizationBalance: string;
  reorganizationFactor: string;
  relevantCommitMinLines: number;
  tolerance: string;
  timeZone: string;
  excludesLogins: boolean;
  exampleStudents: number;
  exampleFairShare: string;
  exampleFullShare: string;
  exampleFullGrade: string;
  exampleLowShare: string;
  exampleLowGrade: string;
}

/** GET /api/config: what the pages need from milestones.properties and grading.properties. */
export interface AppConfig {
  periods: PeriodOption[];
  /** default value of the "Until" field of the Custom period (yyyy-MM-ddTHH:mm, end of today) */
  customUntil: string;
  gradingExplanation: GradingExplanation;
}

/** Active group, as listed in the home and in "Manage groups". */
export interface GroupSummary {
  id: number;
  name: string;
  repositoryUrl: string;
  owner: string;
  repository: string;
  members: number;
}

/** Archived group ("Manage groups", administrators). */
export interface ArchivedGroup extends GroupSummary {
  archivedOn: string;
}

/** GET /api/groups/archived */
export interface ArchivedGroupsResponse {
  groups: ArchivedGroup[];
}

export interface Member {
  login: string;
  name: string;
}

/** Card of "Contributors and members": a GitHub contributor, or a member without commits. */
export interface GroupCard {
  login: string;
  avatarUrl: string;
  htmlUrl: string;
  contributions: number | null;   // null when GitHub could not be read
  member: boolean;
}

/** GET /api/groups/{id} */
export interface GroupDetail {
  group: GroupSummary;
  members: Member[];
  cards: GroupCard[];
  contributorsCount: number | null;
  contributorsError: string | null;
  /** students that may receive a linked identity (administrators only; empty for the others) */
  aliasTargets: string[];
}

export interface ManualIdentity {
  key: string;
  identity: string;
}

export interface GradeDetails {
  effectiveLines: number;
  activeDays: number;
  relevantCommits: number;
  indexShare: number;
  ratio: number;
}

/** One author of the dashboard. */
export interface ContributionRow {
  author: string;
  commits: number;
  totalAdditions: number;
  totalDeletions: number;
  percentage: number;
  linked: boolean;
  gitIdentities: string[];
  identityKey: string | null;
  manualIdentities: ManualIdentity[];
  /** milestone periods */
  grade?: number | null;
  gradeDetails?: GradeDetails | null;
  /** period All */
  milestoneGrades?: Record<string, number | null> | null;
  total?: number | null;
}

export interface GradingInfo {
  maxPoints?: number;
  tolerance?: number;
  fairShare?: number;
  students: number;
  excludedCommits?: number;
  excludedLines?: number;
  provisional?: boolean;
  provisionalMilestones?: string[];
}

/** GET /api/groups/{id}/contributions?period=&since=&until= */
export interface ContributionsReport {
  period: string;
  periodText: string;
  /** start and end of the period (yyyy-MM-ddTHH:mm in the milestone zone); since is null for All */
  since?: string | null;
  until?: string | null;
  totalCommits: number;
  truncated: boolean;
  grading: GradingInfo | null;
  rows: ContributionRow[];
}

export interface CommitRow {
  day: string;          // "Oct 22, 2026"
  time: string;         // "11:29"
  /** raw date and time (yyyy-MM-ddTHH:mm in the milestone zone) */
  date?: string | null;
  message: string;
  sha: string;
  url: string | null;   // only https://github.com/ links
  identity: string | null;
  manual: boolean;
}

export interface CommitActivity {
  granularity: 'day' | 'week';
  title: string;
  summary: string;
  ariaLabel: string;
  labels: string[];
  tooltips: string[];
  series: { key: string; label: string; counts: number[] }[];
  /** raw first day of each column (yyyy-MM-dd), commits and active days */
  starts?: string[];
  total?: number;
  activeDays?: number;
}

/** GET /api/groups/{id}/commits?author=&period=&since=&until= */
export interface CommitHistory {
  author: string;
  period: string;
  periodText: string;
  since?: string | null;
  until?: string | null;
  /** invalid period: the backend falls back to All and says why */
  periodWarning: string | null;
  customSince: string;
  customUntil: string;
  periods: PeriodOption[];
  alsoCounted: string[];
  truncated: boolean;
  commits: CommitRow[];
  activity: CommitActivity | null;
}

/** GET /api/groups/{id}/members (administrators) */
export interface GroupMembersResponse {
  group: { id: number; name: string };
  members: Member[];
}

/** GET /api/grades (administrators): groups and their students; grades come per group. */
export interface GradebookResponse {
  groups: { id: number; name: string; students: string[] }[];
}

/** GET /api/grades/{groupId} */
export interface GroupGrades {
  groupId: number;
  students: { login: string; m1: number | null; m2: number | null; m3: number | null; total: number | null }[];
  provisionalMilestones: string[];
  excludedCommits: number;
}
