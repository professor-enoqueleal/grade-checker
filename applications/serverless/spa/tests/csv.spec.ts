import { describe, expect, it } from 'vitest';
import { CSV_HEADER, csvCell, decimal, gradesCsv } from '@/utils/csv';

describe('class grades CSV (was /grades.csv)', () => {
  it('neutralizes formulas and quotes separators', () => {
    expect(csvCell('=SUM(A1)')).toBe("'=SUM(A1)");
    expect(csvCell('-1')).toBe("'-1");
    expect(csvCell('@x')).toBe("'@x");
    expect(csvCell('a;b')).toBe('"a;b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(csvCell('')).toBe('');
    expect(csvCell(null)).toBe('');
  });

  it('writes decimals with a comma, half up', () => {
    expect(decimal(2.5)).toBe('2,50');
    expect(decimal(1.005)).toBe('1,01');
    expect(decimal(null)).toBe('');
  });

  it('has BOM, header, groups sorted by name, CRLF and skips groups without students', () => {
    const csv = gradesCsv([
      { name: 'zeta', students: ['ana'], grades: new Map([['ana', { m1: 3, m2: 2.25, m3: null, total: 5.25 }]]), provisionalMilestones: ['m3'], error: null },
      { name: 'alpha', students: ['Bob'], grades: null, provisionalMilestones: [], error: 'GitHub rate limit' },
      { name: 'empty', students: [], grades: null, provisionalMilestones: [], error: null },
    ]);
    expect(csv.startsWith('﻿' + CSV_HEADER + '\r\n')).toBe(true);
    const lines = csv.slice(1).split('\r\n');
    expect(lines).toEqual([
      CSV_HEADER,
      'alpha;Bob;;;;;;GitHub rate limit',
      'zeta;ana;3,00;2,25;;5,25;m3;',
      '',
    ]);
  });
});
