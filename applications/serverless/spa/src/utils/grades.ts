import type { ContributionRow, ContributionsReport, GradeDetails } from '@/api/types';
import { t } from '@/i18n';
import { formatGrade } from './format';

/** Grade column of the dashboard table. */
export interface GradeColumn {
  header: string;
  cell: (row: ContributionRow) => { text: string; title?: string };
}

export const MILESTONE_KEYS = ['m1', 'm2', 'm3'] as const;

const hasValue = (v: number | null | undefined): v is number => v !== null && v !== undefined;

/** Tooltip of a milestone grade: the student's numbers. Texts in the current language (call it inside a computed). */
export function gradeTitle(d: GradeDetails | null | undefined): string {
  if (!d) return t('grading.notStudent');
  return t('grading.details', {
    lines: d.effectiveLines, days: d.activeDays, commits: d.relevantCommits,
    share: (d.indexShare * 100).toFixed(1), ratio: d.ratio,
  });
}

/** "M1", "M2 (in progress)" */
export function milestoneHeader(i: number, provisional: boolean): string {
  return t('grading.milestoneColumn', { n: i + 1 }) + (provisional ? t('common.inProgress') : '');
}

/** Columns for the period: milestone -> Grade (0–3); all -> M1, M2, M3, Total (0–9); custom -> none. */
export function gradeColumns(report: ContributionsReport): GradeColumn[] {
  const grading = report.grading;
  if (!grading) return [];
  if (report.period.startsWith('m')) {
    const inProgress = grading.provisional ? t('common.inProgress') : '';
    return [{
      header: t('grading.gradeColumn') + inProgress,
      cell: (row) => ({
        text: formatGrade(row.grade) + (hasValue(row.grade) ? inProgress : ''),
        title: gradeTitle(row.gradeDetails),
      }),
    }];
  }
  if (report.period === 'all') {
    const provisional = grading.provisionalMilestones ?? [];
    const columns: GradeColumn[] = MILESTONE_KEYS.map((key, i) => ({
      header: milestoneHeader(i, provisional.includes(key)),
      cell: (row) => ({ text: formatGrade(row.milestoneGrades ? row.milestoneGrades[key] : null) }),
    }));
    columns.push({ header: t('grading.totalColumn'), cell: (row) => ({ text: formatGrade(row.total) }) });
    return columns;
  }
  return [];
}

/** Notice under the period buttons: commits not counted, or no students. */
export function gradingNotice(report: ContributionsReport, isAdmin: boolean): string | null {
  const g = report.grading;
  if (g && (g.excludedCommits ?? 0) > 0) {
    return t('grading.excluded', { count: g.excludedCommits ?? 0 }) + (isAdmin ? t('grading.excludedAdmin') : '');
  }
  if (g && g.students === 0) return t('grading.noStudents');
  return null;
}
