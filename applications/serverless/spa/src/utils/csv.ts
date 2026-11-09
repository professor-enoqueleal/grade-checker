/**
 * Class grades CSV, generated in the browser (it was /grades.csv): pt-BR spreadsheet format, same columns as before.
 * UTF-8 with BOM (Excel), ";" separator, decimal comma, CRLF, formulas neutralized.
 */
import { round2 } from './format';

export const CSV_HEADER = 'group;student;m1;m2;m3;total;provisional;error';

export interface CsvGroup {
  name: string;
  students: string[];
  /** grades by login (lower case), or null when the group failed */
  grades: Map<string, { m1: number | null; m2: number | null; m3: number | null; total: number | null }> | null;
  provisionalMilestones: string[];
  error: string | null;
}

/** Neutralizes formulas (=, +, -, @, tab, CR) and quotes fields with ";", '"' or line breaks. */
export function csvCell(value: string | null | undefined): string {
  if (!value) return '';
  let v = value;
  if (/^[=+\-@\t\r]/.test(v)) v = "'" + v;
  if (/[;"\n\r]/.test(v)) v = '"' + v.replace(/"/g, '""') + '"';
  return v;
}

/** 2 decimal places with a comma (pt-BR); empty when absent. */
export function decimal(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : round2(value).toFixed(2).replace('.', ',');
}

/** Groups sorted by name (then as given), students in the given order; groups without students are skipped. */
export function gradesCsv(groups: CsvGroup[]): string {
  const lines = [CSV_HEADER];
  const sorted = [...groups].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  for (const group of sorted) {
    if (group.students.length === 0) continue;
    const provisional = group.grades ? group.provisionalMilestones.join(',') : '';
    for (const login of group.students) {
      const s = group.grades?.get(login.toLowerCase()) ?? null;
      const cells = [group.name, login, decimal(s?.m1), decimal(s?.m2), decimal(s?.m3), decimal(s?.total), provisional, group.error ?? ''];
      lines.push(cells.map(csvCell).join(';'));
    }
  }
  return '﻿' + lines.join('\r\n') + '\r\n';
}

/** Saves the text as a file (download). */
export function download(filename: string, text: string, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
