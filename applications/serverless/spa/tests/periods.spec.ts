import { afterEach, describe, expect, it } from 'vitest';
import { i18n } from '@/i18n';
import { formatDay, formatDayTime, formatShortDay, formatTime, formatWeekday } from '@/i18n/dates';
import { activityTexts, periodHint, periodLabel, periodText } from '@/i18n/periods';
import type { CommitActivity, PeriodOption } from '@/api/types';

/** Periods, dates and frequency chart in both languages (openspec capability serverless-i18n). */
afterEach(() => {
  i18n.global.locale.value = 'en';
});

const pt = () => { i18n.global.locale.value = 'pt-BR'; };

const m3: PeriodOption = { key: 'm3', label: 'Milestone 3', enabled: false, hint: 'Starts on Oct 30, 2026', start: '2026-10-30', end: '2026-11-19' };
const m1: PeriodOption = { key: 'm1', label: 'Milestone 1', enabled: true, hint: 'x', start: '2026-08-13', end: '2026-09-17' };
const all: PeriodOption = { key: 'all', label: 'All', enabled: true, hint: 'All history', start: null, end: null };

describe('dates', () => {
  it('English: the formats of the backend', () => {
    expect(formatDay('2026-10-21')).toBe('Oct 21, 2026');
    expect(formatDayTime('2026-09-01T00:00')).toBe('Sep 1, 2026 00:00');
    expect(formatShortDay('2026-10-21')).toBe('Oct 21');
    expect(formatWeekday('2026-10-21')).toBe('Wed, Oct 21, 2026');
    expect(formatTime('2026-10-21T09:05')).toBe('09:05');
  });

  it('Portuguese: dd/MM/yyyy, never shifted by the browser zone', () => {
    pt();
    expect(formatDay('2026-10-21')).toBe('21/10/2026');
    expect(formatDay('2026-01-01T00:00')).toBe('01/01/2026');
    expect(formatDayTime('2026-09-30T23:59')).toBe('30/09/2026 23:59');
    expect(formatShortDay('2026-10-21')).toBe('21/10');
    expect(formatWeekday('2026-10-21')).toBe('qua., 21/10/2026');
  });
});

describe('period buttons', () => {
  it('names by key: Milestone / Marco, All / Tudo', () => {
    expect([m1, m3, all].map(periodLabel)).toEqual(['Milestone 1', 'Milestone 3', 'All']);
    pt();
    expect([m1, m3, all].map(periodLabel)).toEqual(['Marco 1', 'Marco 3', 'Tudo']);
  });

  it('hints: dates, future milestone, not configured, All', () => {
    const notConfigured: PeriodOption = { key: 'm2', label: 'Milestone 2', enabled: false, hint: 'Not configured', start: null, end: null };
    expect([m1, m3, notConfigured, all].map(periodHint))
      .toEqual(['Aug 13, 2026 – Sep 17, 2026', 'Starts on Oct 30, 2026', 'Not configured', 'All history']);
    pt();
    expect([m1, m3, notConfigured, all].map(periodHint))
      .toEqual(['13/08/2026 – 17/09/2026', 'Começa em 30/10/2026', 'Não configurado', 'Todo o histórico']);
  });

  it('without raw dates (older response): the hint of the backend', () => {
    pt();
    expect(periodHint({ key: 'm3', label: 'Milestone 3', enabled: false, hint: 'Starts on Oct 30, 2026' })).toBe('Starts on Oct 30, 2026');
  });
});

describe('period text', () => {
  it('All, milestone and custom', () => {
    expect(periodText({ period: 'all', periodText: 'x', since: null, until: null })).toBe('All history');
    expect(periodText({ period: 'm2', periodText: 'x', since: '2026-09-18T00:00', until: '2026-10-29T23:59' }))
      .toBe('Sep 18, 2026 – Oct 29, 2026');
    expect(periodText({ period: 'custom', periodText: 'x', since: '2026-09-01T00:00', until: '2026-09-30T23:59' }))
      .toBe('Sep 1, 2026 00:00 – Sep 30, 2026 23:59');
    pt();
    expect(periodText({ period: 'all', periodText: 'x', since: null, until: null })).toBe('Todo o histórico');
    expect(periodText({ period: 'm2', periodText: 'x', since: '2026-09-18T00:00', until: '2026-10-29T23:59' }))
      .toBe('18/09/2026 – 29/10/2026');
  });

  it('without since (older response): the text of the backend', () => {
    pt();
    expect(periodText({ period: 'all', periodText: 'All history' })).toBe('All history');
  });
});

describe('frequency chart', () => {
  const weekly: CommitActivity = {
    granularity: 'week', title: 'Commits per week', summary: 'b', ariaLabel: 'c', labels: ['Oct 19'], tooltips: ['Week of Oct 19, 2026'],
    series: [{ key: 'm2', label: 'M2', counts: [3] }, { key: 'outside', label: 'Outside milestones', counts: [1] }],
    starts: ['2026-10-19', '2026-10-26'], total: 4, activeDays: 1,
  };

  it('English: the texts of the backend', () => {
    const texts = activityTexts(weekly);
    expect(texts.title).toBe('Commits per week');
    expect(texts.summary).toBe('4 commits · 1 active day');
    expect(texts.ariaLabel).toBe('4 commits on 1 active day, per week');
    expect(texts.labels).toEqual(['Oct 19', 'Oct 26']);
    expect(texts.tooltips).toEqual(['Week of Oct 19, 2026', 'Week of Oct 26, 2026']);
    expect(weekly.series.map(texts.seriesLabel)).toEqual(['M2', 'Outside milestones']);
  });

  it('Portuguese, per day', () => {
    pt();
    const texts = activityTexts({ ...weekly, granularity: 'day', total: 1, activeDays: 2 });
    expect(texts.title).toBe('Commits por dia');
    expect(texts.summary).toBe('1 commit · 2 dias ativos');
    expect(texts.ariaLabel).toBe('1 commit em 2 dias ativos, por dia');
    expect(texts.labels).toEqual(['19/10', '26/10']);
    expect(texts.tooltips[0]).toBe('seg., 19/10/2026');
    expect(weekly.series.map(texts.seriesLabel)).toEqual(['M2', 'Fora dos marcos']);
    expect(activityTexts(weekly).tooltips[0]).toBe('Semana de 19/10/2026');
  });

  it('without raw values (older response): the texts of the backend', () => {
    pt();
    const { starts, total, activeDays, ...older } = weekly;
    void starts; void total; void activeDays;
    expect(activityTexts(older).title).toBe('Commits per week');
    expect(activityTexts(older).tooltips).toEqual(['Week of Oct 19, 2026']);
  });
});
