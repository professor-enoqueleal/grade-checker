import { t } from '@/i18n';
import { formatDay, formatDayTime, formatShortDay, formatWeekday } from '@/i18n/dates';
import type { CommitActivity, PeriodOption } from '@/api/types';

/**
 * Texts of the periods and of the frequency chart in the current language (openspec capability serverless-i18n), from the
 * raw values of the API. When a response has no raw values (older contract), the text formatted by the backend is used.
 */
const MILESTONE = /^m(\d+)$/;

/** "Milestone 1" / "Marco 1", "All" / "Tudo"; any other key keeps the backend label. */
export function periodLabel(option: Pick<PeriodOption, 'key' | 'label'>): string {
  const milestone = MILESTONE.exec(option.key);
  if (milestone) return t('period.milestone', { n: Number(milestone[1]) });
  if (option.key === 'all') return t('period.all');
  return option.label;
}

/** Tooltip of a period button: dates of the milestone, "Starts on …", "Not configured" or "All history". */
export function periodHint(option: PeriodOption): string {
  if (option.start === undefined) return option.hint;
  if (option.key === 'all') return t('period.allHistory');
  if (!option.start || !option.end) return t('period.notConfigured');
  if (!option.enabled) return t('period.startsOn', { date: formatDay(option.start) });
  return t('period.range', { start: formatDay(option.start), end: formatDay(option.end) });
}

/** Period of a report: "All history", the dates of a milestone, or date and time of a custom period. */
export function periodText(report: { period: string; periodText: string; since?: string | null; until?: string | null }): string {
  if (report.since === undefined) return report.periodText;
  if (report.since === null) return t('period.allHistory');
  if (!report.until) return report.periodText;
  if (MILESTONE.test(report.period)) return t('period.range', { start: formatDay(report.since), end: formatDay(report.until) });
  return t('period.range', { start: formatDayTime(report.since), end: formatDayTime(report.until) });
}

export interface ActivityTexts {
  title: string;
  summary: string;
  ariaLabel: string;
  labels: string[];
  tooltips: string[];
  seriesLabel: (series: CommitActivity['series'][number]) => string;
}

/** Title, summary, axis, tooltips and series names of the frequency chart. */
export function activityTexts(activity: CommitActivity): ActivityTexts {
  const { starts, total, activeDays } = activity;
  if (!starts || total === undefined || activeDays === undefined) {
    return { title: activity.title, summary: activity.summary, ariaLabel: activity.ariaLabel, labels: activity.labels,
      tooltips: activity.tooltips, seriesLabel: (s) => s.label };
  }
  const daily = activity.granularity === 'day';
  const commits = t('commits.count', { n: total }, total);
  const days = t('activity.activeDays', { n: activeDays }, activeDays);
  return {
    title: t(daily ? 'activity.titleDay' : 'activity.titleWeek'),
    summary: t('activity.summary', { commits, days }),
    ariaLabel: t(daily ? 'activity.ariaDay' : 'activity.ariaWeek', { commits, days }),
    labels: starts.map(formatShortDay),
    tooltips: starts.map((start) => (daily ? formatWeekday(start) : t('activity.week', { date: formatDay(start) }))),
    seriesLabel: (s) => {
      const milestone = MILESTONE.exec(s.key);
      if (milestone) return `M${milestone[1]}`;
      return s.key === 'outside' ? t('activity.outside') : s.label;
    },
  };
}
