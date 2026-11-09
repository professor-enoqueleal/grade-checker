import { describe, expect, it } from 'vitest';
import { endOfToday, formatGrade, formatMax, formatPercentage, gitHubLink, repositoryLabel, shortSha } from '@/utils/format';

describe('formatting', () => {
  it('formats grades and percentages with 2 places', () => {
    expect(formatGrade(null)).toBe('—');
    expect(formatGrade(undefined)).toBe('—');
    expect(formatGrade(2)).toBe('2.00');
    expect(formatGrade(1.005)).toBe('1.01');
    expect(formatPercentage(12.5)).toBe('12.50%');
  });

  it('formats axis maxima', () => {
    expect(formatMax(9)).toBe('9');
    expect(formatMax(4.5)).toBe('4.5');
  });

  it('shortens repository URLs and only links github.com', () => {
    expect(repositoryLabel('https://github.com/octocat/Hello-World/')).toBe('octocat/Hello-World');
    expect(repositoryLabel('https://www.github.com/a/b')).toBe('a/b');
    expect(gitHubLink('https://github.com/a/b')).toBe('https://github.com/a/b');
    expect(gitHubLink('javascript:alert(1)')).toBeNull();
  });

  it('shows 7 characters of the hash', () => {
    expect(shortSha('0123456789abcdef')).toBe('0123456');
    expect(shortSha('abc')).toBe('abc');
  });

  it('limits the custom period to the end of today', () => {
    expect(endOfToday(new Date(2026, 10, 8, 15, 30))).toBe('2026-11-08T23:59');
  });
});
