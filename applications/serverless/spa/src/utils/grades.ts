import type { ContributionRow, ContributionsReport, GradeDetails } from '@/api/types';
import { formatGrade } from './format';

/** Grade column of the dashboard table. */
export interface GradeColumn {
  header: string;
  cell: (row: ContributionRow) => { text: string; title?: string };
}

export const MILESTONE_KEYS = ['m1', 'm2', 'm3'] as const;

const hasValue = (v: number | null | undefined): v is number => v !== null && v !== undefined;

/** Tooltip of a milestone grade: the student's numbers. */
export function gradeTitle(d: GradeDetails | null | undefined): string {
  if (!d) return 'Not a student of this group: no grade';
  return `Effective lines: ${d.effectiveLines} · Active days: ${d.activeDays} · Relevant commits: ${d.relevantCommits}`
    + ` · Index share: ${(d.indexShare * 100).toFixed(1)}% · Ratio to fair share: ${d.ratio}`;
}

/** Columns for the period: milestone -> Grade (0–3); all -> M1, M2, M3, Total (0–9); custom -> none. */
export function gradeColumns(report: ContributionsReport): GradeColumn[] {
  const grading = report.grading;
  if (!grading) return [];
  if (report.period.startsWith('m')) {
    const inProgress = grading.provisional ? ' (in progress)' : '';
    return [{
      header: 'Grade (0–3)' + inProgress,
      cell: (row) => ({
        text: formatGrade(row.grade) + (hasValue(row.grade) ? inProgress : ''),
        title: gradeTitle(row.gradeDetails),
      }),
    }];
  }
  if (report.period === 'all') {
    const provisional = grading.provisionalMilestones ?? [];
    const columns: GradeColumn[] = MILESTONE_KEYS.map((key, i) => ({
      header: `M${i + 1}` + (provisional.includes(key) ? ' (in progress)' : ''),
      cell: (row) => ({ text: formatGrade(row.milestoneGrades ? row.milestoneGrades[key] : null) }),
    }));
    columns.push({ header: 'Total (0–9)', cell: (row) => ({ text: formatGrade(row.total) }) });
    return columns;
  }
  return [];
}

/** Notice under the period buttons: commits not counted, or no students. */
export function gradingNotice(report: ContributionsReport, isAdmin: boolean): string | null {
  const g = report.grading;
  if (g && (g.excludedCommits ?? 0) > 0) {
    return `${g.excludedCommits} commits not counted in grades (authors not linked to group members).`
      + (isAdmin ? ' Use "Link to student" to count them for a student.' : '');
  }
  if (g && g.students === 0) return 'No students registered in this group: grades are not available.';
  return null;
}
