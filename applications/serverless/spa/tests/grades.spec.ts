import { describe, expect, it } from 'vitest';
import type { ContributionRow, ContributionsReport } from '@/api/types';
import { gradeColumns, gradeTitle, gradingNotice } from '@/utils/grades';

const row = (extra: Partial<ContributionRow>): ContributionRow => ({
  author: 'ana', commits: 1, totalAdditions: 10, totalDeletions: 2, percentage: 100, linked: true,
  gitIdentities: [], identityKey: null, manualIdentities: [], ...extra,
});

const report = (period: string, grading: ContributionsReport['grading']): ContributionsReport => ({
  period, periodText: '', totalCommits: 1, truncated: false, grading, rows: [],
});

describe('grade columns of the dashboard', () => {
  it('milestone: one Grade column, "(in progress)" while the milestone runs', () => {
    const columns = gradeColumns(report('m3', { students: 2, provisional: true }));
    expect(columns.map((c) => c.header)).toEqual(['Grade (0–3) (in progress)']);
    expect(columns[0].cell(row({ grade: 2.5, gradeDetails: null })).text).toBe('2.50 (in progress)');
    expect(columns[0].cell(row({ grade: null })).text).toBe('—');
    expect(columns[0].cell(row({ grade: null })).title).toBe('Not a student of this group: no grade');
  });

  it('all: M1, M2, M3 and Total', () => {
    const columns = gradeColumns(report('all', { students: 2, provisionalMilestones: ['m2'] }));
    expect(columns.map((c) => c.header)).toEqual(['M1', 'M2 (in progress)', 'M3', 'Total (0–9)']);
    const r = row({ milestoneGrades: { m1: 3, m2: 1.5, m3: null }, total: 4.5 });
    expect(columns.map((c) => c.cell(r).text)).toEqual(['3.00', '1.50', '—', '4.50']);
  });

  it('custom period or no group: no grade columns', () => {
    expect(gradeColumns(report('custom', null))).toEqual([]);
  });

  it('explains the grade in the tooltip', () => {
    expect(gradeTitle({ effectiveLines: 120, activeDays: 4, relevantCommits: 3, indexShare: 0.256, ratio: 1.02 }))
      .toBe('Effective lines: 120 · Active days: 4 · Relevant commits: 3 · Index share: 25.6% · Ratio to fair share: 1.02');
  });

  it('notices: commits not counted, or no students', () => {
    expect(gradingNotice(report('m1', { students: 2, excludedCommits: 3 }), true))
      .toBe('3 commits not counted in grades (authors not linked to group members). Use "Link to student" to count them for a student.');
    expect(gradingNotice(report('m1', { students: 2, excludedCommits: 3 }), false))
      .toBe('3 commits not counted in grades (authors not linked to group members).');
    expect(gradingNotice(report('m1', { students: 0 }), false)).toBe('No students registered in this group: grades are not available.');
    expect(gradingNotice(report('all', { students: 2 }), false)).toBeNull();
  });
});
